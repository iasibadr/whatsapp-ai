package live.pharmaradio.whatsappai;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.telecom.Call;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

final class PhoneAgent {
    private final CallBridgeService service;
    private final Call call;
    private final String brief;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean attached = new AtomicBoolean();
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Consumer<String> diagnostic;
    private volatile String stage = "service audio local";
    private final OkHttpClient http;
    private final DirectOpenAi direct;
    private volatile WebSocket websocket;
    private volatile ParcelFileDescriptor audioSocket;
    private volatile IBinder helper;
    private volatile DataOutputStream audioOutput;
    private volatile boolean sessionReady;
    private volatile long playoutUntil;
    private long receivedAudioBytes;
    private long capturedAudioBytes;
    private long playedAudioBytes;
    private final ApiUsage usage = new ApiUsage();
    private volatile String model = "";
    private volatile String endReason = "Fin de l’appel ou reprise demandée.";
    private final String localCallId = java.util.UUID.randomUUID().toString();
    private String historyPolicy;
    private final CallOutcome outcome = new CallOutcome();
    private final long created = System.currentTimeMillis();
    private final java.util.concurrent.ScheduledExecutorService signals = Executors.newSingleThreadScheduledExecutor();
    private android.os.PowerManager.WakeLock wakeLock;
    private long lastWakeRenewal;
    private boolean costAlerted;
    private long expires;
    private final Object powerLock = new Object();

    PhoneAgent(CallBridgeService service, Call call) {
        this(service, call, "", MissionDossier.empty());
    }

    PhoneAgent(CallBridgeService service, Call call, PreparedMission mission) {
        this(service, call, mission.brief, mission.dossier);
    }

    private PhoneAgent(CallBridgeService service, Call call, String brief, MissionDossier dossier) {
        this.service = service;
        this.call = call;
        this.brief = brief;
        expires = created + java.util.concurrent.TimeUnit.DAYS.toMillis(service == null ? 1 : AppSettings.historyDays(service));
        http = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(12, TimeUnit.SECONDS)
                .dns(hostname -> {
                    if (!hostname.equals("api.openai.com")) throw new java.net.UnknownHostException("Seul OpenAI est autorisé");
                    return okhttp3.Dns.SYSTEM.lookup(hostname);
                })
                .readTimeout(25, TimeUnit.SECONDS).pingInterval(10, TimeUnit.SECONDS).build();
        direct = new DirectOpenAi(AutonomousApp.context(), http, ModelSettings.read(AutonomousApp.context()), dossier);
        saveUsage("Préparation IA");
    }

    void prepare() {
        main.postDelayed(() -> {
            if (!closed.get() && !sessionReady) fail("Préparation trop longue ; réponse manuelle disponible.");
        }, 30000);
        work.execute(() -> {
            try {
                {
                    Parcel reply = helperRequest(1);
                    JSONObject result;
                    try { result = new JSONObject(reply.readString()); }
                    finally { reply.recycle(); }
                    if (!result.optBoolean("available")) throw new IllegalStateException("Pont audio indisponible");
                }
                stage = "canal audio privé";
                Parcel descriptorReply = helperRequest(3);
                ParcelFileDescriptor descriptor;
                try { descriptor = descriptorReply.readTypedObject(ParcelFileDescriptor.CREATOR); }
                finally { descriptorReply.recycle(); }
                if (descriptor == null) throw new IllegalStateException("Canal prive absent");
                audioSocket = descriptor;
                try (ParcelFileDescriptor connection = descriptor) {
                    DataOutputStream output = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(connection));
                    DataInputStream input = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(connection));
                    long nonce = new java.security.SecureRandom().nextLong();
                    output.writeLong(nonce);
                    output.flush();
                    if (input.readLong() != nonce) throw new IllegalStateException("Reponse du canal prive invalide");
                } finally { audioSocket = null; }
                stage = "autorisation OpenAI";
                JSONObject mission = new JSONObject();
                if (!brief.isEmpty()) mission.put("direction", "outgoing").put("brief", brief);
                JSONObject token = new JSONObject(backend("/api/call-token", mission));
                model = token.getString("model");
                if (closed.get()) return;
                stage = "connexion OpenAI";
                Request request = new Request.Builder().url("wss://api.openai.com/v1/realtime?model=" + token.getString("model"))
                        .header("Authorization", "Bearer " + token.getString("value")).build();
                websocket = http.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onMessage(WebSocket socket, String text) {
                        if (closed.get()) return;
                        try { event(new JSONObject(text)); }
                        catch (Exception error) { fail("Erreur du pont vocal ; reprenez la conversation."); }
                    }
                    @Override
                    public void onFailure(WebSocket socket, Throwable error, Response response) {
                        fail("Échec " + stage + " : " + error.getClass().getSimpleName() + (response == null ? "" : " HTTP " + response.code()));
                    }
                    @Override
                    public void onClosed(WebSocket socket, int code, String reason) {
                        fail("Session vocale terminée (code " + code + ") ; réponse manuelle disponible.");
                    }
                });
            } catch (Exception error) {
                fail("Échec " + stage + " : " + error.getClass().getSimpleName() + ". " + error.getMessage());
            }
        });
    }

    static void diagnose(Consumer<String> callback) {
        PhoneAgent agent = new PhoneAgent(null, null);
        agent.diagnostic = callback;
        agent.prepare();
    }

    static PhoneAgent transcribe(File recording, Consumer<String> success, Consumer<String> failure) {
        PhoneAgent agent = new PhoneAgent(null, null);
        agent.work.execute(() -> {
            try {
                String text = agent.direct.transcribe(recording);
                if (!agent.closed.get()) agent.main.post(() -> success.accept(text));
            } catch (Exception error) {
                if (!agent.closed.get()) agent.main.post(() -> failure.accept("Dictée indisponible (" + error.getClass().getSimpleName() + "). Vous pouvez écrire les consignes."));
            } finally {
                recording.delete();
                agent.close();
            }
        });
        return agent;
    }

    private Parcel helperRequest(int command) throws Exception {
        long deadline = android.os.SystemClock.elapsedRealtime() + 7000;
        while (helper == null && !closed.get() && android.os.SystemClock.elapsedRealtime() < deadline) {
            helper = BridgeProvider.getHelper();
            if (helper == null) Thread.sleep(200);
        }
        if (closed.get() || helper == null) throw new IllegalStateException("Service Binder non publie");
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(BridgeProvider.DESCRIPTOR);
            if (!helper.transact(command, request, reply, 0)) throw new IllegalStateException("Commande Binder refusee");
            reply.readException();
            return reply;
        } catch (Exception error) { reply.recycle(); throw error; }
        finally { request.recycle(); }
    }

    private String backend(String path, JSONObject body) throws Exception {
        return direct.localRequest(path, body);
    }

    private void event(JSONObject event) throws Exception {
        String type = event.getString("type");
        if (type.equals("error")) {
            fail("Erreur Realtime : " + errorCode(event.optJSONObject("error")) + ". Conversation rendue au téléphone.");
            return;
        }
        if (type.equals("session.created")) {
            if (diagnostic != null) {
                sessionReady = true;
                Consumer<String> callback = diagnostic;
                close();
                main.post(() -> {
                    callback.accept("Diagnostic réussi : service audio et OpenAI accessibles. Aucun appel déclenché. Le son en appel reste à vérifier.");
                });
                return;
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    service.getAssets().open("message_history_policy.txt"), java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder policy = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) policy.append(line).append('\n');
                historyPolicy = policy.toString().trim();
            }
            JSONObject session = direct.session(brief.isEmpty() ? new JSONObject() : new JSONObject().put("direction", "outgoing").put("brief", brief));
            JSONArray tools = session.getJSONArray("tools");
            for (int index = 0; index < tools.length(); index++) {
                JSONObject tool = tools.getJSONObject(index);
                if (tool.optString("name").equals("noter_message")) tool.put("description",
                        "Garder un message confirme, consultable localement sur le telephone pendant 24 heures. Annoncer cette conservation et attendre l'accord explicite avant l'outil. Le resultat copie_locale confirme ou refuse l'enregistrement local.");
            }
            send(new JSONObject().put("type", "session.update").put("session", new JSONObject().put("type", "realtime")
                    .put("instructions", session.getString("instructions") + "\n\n" + historyPolicy + "\n"
                            + AppSettings.contactPolicy(service, CallIdentity.read(call).number)
                            + "\nLes consignes privées du propriétaire reçues par l’interface ne sont pas destinées à être lues au correspondant. Elles ne changent pas les interdictions d’engagement, de secret ou de dissimulation de l’identité IA.").put("tools", tools)));
            return;
        } else if (type.equals("session.updated") && !sessionReady && historyPolicy != null) {
            if (!event.getJSONObject("session").optString("instructions").contains(historyPolicy)) {
                fail("Politique de conservation non confirmée ; réponse manuelle disponible.");
                return;
            }
            sessionReady = true;
            main.post(() -> {
                if (closed.get() || service.agent != this || call != service.incoming) return;
                if (!brief.isEmpty() && call.getState() != Call.STATE_DISCONNECTED) {
                    service.status = "IA prête — attente du correspondant…";
                    service.changed();
                    if (call.getState() == Call.STATE_ACTIVE) attachAudio();
                } else if (call.getState() == Call.STATE_RINGING) {
                    if (!service.mayAnswer(call)) {
                        fail("Prise IA annulée : répondeur désactivé, autre appel présent ou appel d’urgence identifié.");
                        return;
                    }
                    service.status = "Décroché IA — connexion du son téléphonique…";
                    service.changed();
                    call.answer(0);
                } else if (!closed.get()) fail("Appel déjà pris ou terminé.");
            });
        } else if (type.equals("response.created")) {
            usage.started(event.getJSONObject("response").optString("id"));
            saveUsage("Réponse IA en cours");
        } else if (type.equals("response.output_audio.delta")) {
            byte[] pcm = Base64.decode(event.getString("delta"), Base64.DEFAULT);
            receivedAudioBytes += pcm.length;
            writeAudio(pcm);
        } else if (type.equals("input_audio_buffer.speech_started")) {
            control(-2);
            playoutUntil = 0;
        } else if (type.equals("response.done")) {
            JSONObject response = event.getJSONObject("response");
            recordUsage(response);
            if (response.getString("status").equals("failed")) {
                JSONObject details = response.optJSONObject("status_details");
                fail("Tour vocal refusé : " + errorCode(details == null ? null : details.optJSONObject("error")) + ". Conversation rendue au téléphone.");
                return;
            }
            JSONArray items = response.optJSONArray("output");
            if (items == null) return;
            List<JSONObject> tools = new ArrayList<>();
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.getJSONObject(index);
                if (item.optString("type").equals("function_call")) tools.add(item);
            }
            if (!tools.isEmpty() && !closed.get()) work.execute(() -> processTools(tools));
        }
    }

    void attachAudio() {
        if (!sessionReady || closed.get() || !attached.compareAndSet(false, true)) return;
        if (!service.hasExclusiveCall(call)) {
            fail("Plusieurs appels : IA arrêtée pour ne pas mélanger les conversations.");
            return;
        }
        service.setMuted(true);
        work.execute(() -> {
            try {
                Parcel reply = helperRequest(2);
                ParcelFileDescriptor socket;
                try { socket = reply.readTypedObject(ParcelFileDescriptor.CREATOR); }
                finally { reply.recycle(); }
                if (socket == null) throw new IllegalStateException("Descripteur audio absent");
                audioSocket = socket;
                DataOutputStream output = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(socket));
                audioOutput = output;
                DataInputStream input = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(socket));
                JSONObject ready = new JSONObject(input.readUTF());
                if (!ready.optBoolean("ready") || ready.optInt("source") != 3) throw new IllegalStateException("Route audio refusee");
                if (closed.get()) { socket.close(); return; }
                outcome.active();
                saveUsage("Son téléphonique connecté");
                android.os.PowerManager power = service.getSystemService(android.os.PowerManager.class);
                wakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "reponsevoice:call-audio");
                wakeLock.setReferenceCounted(false);
                renewWakeLock();
                main.post(() -> {
                    if (!closed.get()) { service.status = "L’IA converse avec le correspondant. Vous pouvez reprendre."; service.changed(); }
                });
                send(new JSONObject().put("type", "response.create"));
                new Thread(() -> capture(input), "phone-agent-capture").start();
                heartbeat();
            } catch (Exception error) {
                fail("Son téléphonique indisponible ; reprenez la conversation.");
            }
        });
    }

    private void capture(DataInputStream input) {
        try {
            while (!closed.get()) {
                int length = input.readInt();
                if (length <= 0 || length > 65536 || (length & 1) != 0) throw new IllegalStateException("Trame incorrecte");
                byte[] pcm = new byte[length];
                input.readFully(pcm);
                capturedAudioBytes += length;
                if (websocket == null || websocket.queueSize() > 480000) throw new IllegalStateException("Reseau sature");
                send(new JSONObject().put("type", "input_audio_buffer.append")
                        .put("audio", Base64.encodeToString(pcm, Base64.NO_WRAP)));
            }
        } catch (Exception error) {
            fail("Transport audio interrompu ; conversation rendue au téléphone.");
        }
    }

    private void heartbeat() {
        signals.scheduleWithFixedDelay(() -> {
            if (closed.get()) return;
            try {
                control(-3);
                if (android.os.SystemClock.elapsedRealtime() - lastWakeRenewal > java.util.concurrent.TimeUnit.MINUTES.toMillis(5)) renewWakeLock();
            }
            catch (Exception error) { fail("Pont audio arrêté ; conversation rendue au téléphone."); }
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void renewWakeLock() {
        synchronized (powerLock) {
            if (wakeLock != null && !closed.get()) {
                wakeLock.acquire(java.util.concurrent.TimeUnit.MINUTES.toMillis(10));
                lastWakeRenewal = android.os.SystemClock.elapsedRealtime();
            }
        }
    }

    private void processTools(List<JSONObject> tools) {
        try {
            for (JSONObject tool : tools) {
                if (closed.get()) return;
                String name = tool.getString("name");
                JSONObject request = new JSONObject().put("name", name).put("call_id", tool.getString("call_id"))
                        .put("arguments", new JSONObject(tool.getString("arguments")));
                JSONObject result = new JSONObject(backend("/api/tool", request));
                if ((name.equals("noter_message") || name.equals("noter_bilan")) && result.optBoolean("ok")) {
                    try {
                        JSONObject arguments = request.getJSONObject("arguments");
                        if (!arguments.optBoolean("accord")) throw new IllegalArgumentException("Accord absent");
                        StructuredResult structured = name.equals("noter_bilan") ? ResultPayload.consented(arguments) : null;
                        saveMessage(tool.getString("call_id"), structured == null ? arguments.getString("message") : structured.render(), structured);
                        result.put("copie_locale", true).put("consultable_heures", 24);
                        CallNotices.show(service, 4402, "WhatsApp AI", structured == null ? "Un nouveau message confirmé est disponible." : "Un bilan structuré confirmé est disponible (24 h).", MessagesActivity.class);
                    } catch (Exception error) {
                        result.put("ok", false).put("copie_locale", false);
                        main.post(() -> {
                            CallBridgeService.lastError = "Le message n’a pas pu être enregistré dans Messages par contact.";
                            service.changed();
                        });
                    }
                }
                send(new JSONObject().put("type", "conversation.item.create").put("item", new JSONObject()
                        .put("type", "function_call_output").put("call_id", tool.getString("call_id")).put("output", result.toString())));
                if (name.equals("demander_reprise") || name.equals("terminer_discussion")) {
                    long delay = Math.max(800, playoutUntil - android.os.SystemClock.elapsedRealtime() + 800);
                    if (name.equals("demander_reprise")) CallNotices.show(service, 4403, "Reprise demandée", "Votre correspondant souhaite vous parler.", CallMenuActivity.class);
                    main.postDelayed(() -> {
                        if (closed.get() || service.agent != this) return;
                        if (name.equals("terminer_discussion")) service.hangUp("Fin demandée après les adieux de l’IA.");
                        else service.release("Le correspondant souhaite vous parler. La conversation vous est rendue.", false, true);
                    }, delay);
                    return;
                }
                if ((name.equals("noter_message") || name.equals("noter_bilan")) && result.optBoolean("copie_locale")) main.post(() -> {
                    if (!closed.get()) {
                        service.status = "Message enregistré dans Messages par contact (24 h). L’IA poursuit la conversation.";
                        service.changed();
                    }
                });
            }
            send(new JSONObject().put("type", "response.create"));
        } catch (Exception error) {
            fail("Outil vocal indisponible ; conversation rendue au téléphone.");
        }
    }

    private void saveMessage(String toolId, String message, StructuredResult result) throws Exception {
        CallIdentity identity = CallIdentity.read(call);
        String key = identity.number.isEmpty() ? "anonymous:" + localCallId : "tel:" + identity.number;
        String direction = brief.isEmpty() ? "incoming" : "outgoing";
        new MessageStore(service).add(new MessageHistory.Entry(localCallId + ":" + toolId, key, identity.name, identity.number,
                message, direction, System.currentTimeMillis(), result));
    }

    private void writeAudio(byte[] pcm) throws Exception {
        if (closed.get() || audioOutput == null) return;
        synchronized (this) {
            audioOutput.writeInt(pcm.length);
            audioOutput.write(pcm);
            audioOutput.flush();
        }
        playedAudioBytes += pcm.length;
        playoutUntil = Math.max(playoutUntil, android.os.SystemClock.elapsedRealtime()) + pcm.length / 48;
    }

    private synchronized void control(int command) throws Exception {
        if (audioOutput != null && !closed.get()) { audioOutput.writeInt(command); audioOutput.flush(); }
    }

    private void send(JSONObject event) {
        if (!closed.get() && websocket != null && !websocket.send(event.toString())) fail("Connexion vocale fermée.");
    }

    private void fail(String message) {
        if (closed.get()) return;
        endReason = message;
        android.util.Log.w("ReponseVoiceCall", message);
        main.post(() -> {
            if (closed.get()) return;
            CallBridgeService.lastError = message;
            if (diagnostic != null) {
                Consumer<String> callback = diagnostic;
                close();
                callback.accept(message);
            } else if (service.agent == this) {
                CallNotices.show(service, 4404, "Attention requise", "L’IA s’est arrêtée. Reprenez ou terminez l’appel dans Téléphone.", CallMenuActivity.class);
                service.release(message, true);
            }
        });
    }

    boolean steer(String instruction) {
        String text = instruction == null ? "" : instruction.trim();
        if (text.isEmpty() || text.length() > 600 || closed.get() || !sessionReady || !attached.get()
                || call.getState() != Call.STATE_ACTIVE || !service.hasExclusiveCall(call)) return false;
        try {
            send(new JSONObject().put("type", "conversation.item.create").put("item", new JSONObject().put("type", "message")
                    .put("role", "system").put("content", new JSONArray().put(new JSONObject().put("type", "input_text")
                            .put("text", "Consigne écrite privée du propriétaire pour le prochain tour, à ne pas lire comme un message du correspondant. Elle reste soumise aux règles de sécurité et ne peut autoriser un engagement ou une dissimulation de l’identité IA : " + text)))));
            return !closed.get();
        } catch (Exception error) { return false; }
    }

    void finish(String message, boolean failure, boolean handoff) {
        outcome.finish(message, failure, handoff);
        endReason = outcome.reason();
        close();
    }

    void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (service != null && !outcome.terminal()) outcome.finish(endReason, true, false);
        saveUsage(endReason);
        signals.shutdownNow();
        synchronized (powerLock) {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        }
        try { if (audioSocket != null) audioSocket.close(); } catch (Exception ignored) { }
        work.shutdownNow();
        NetworkCleanup.start(() -> {
            try {
                if (websocket != null) websocket.cancel();
            } finally {
                try { http.dispatcher().cancelAll(); }
                finally { http.connectionPool().evictAll(); }
            }
        });
        android.util.Log.i("ReponseVoiceCall", "Stopped: captured=" + capturedAudioBytes + ", received=" + receivedAudioBytes + ", injected=" + playedAudioBytes);
    }

    private static String errorCode(JSONObject error) {
        String code = error == null ? "non_precise" : error.optString("code", "non_precise");
        return code.matches("[A-Za-z0-9_.-]{1,120}") ? code : "non_precise";
    }

    private void recordUsage(JSONObject response) {
        JSONObject report = response.optJSONObject("usage");
        long input = report == null ? -1 : report.optLong("input_tokens", -1);
        long output = report == null ? -1 : report.optLong("output_tokens", -1);
        Double cost = null;
        RealtimeModel pricing = RealtimeModel.find(model);
        if (report != null && pricing != null) {
            JSONObject incoming = report.optJSONObject("input_token_details");
            JSONObject outgoing = report.optJSONObject("output_token_details");
            if (incoming != null && outgoing != null) {
                long textIn = incoming.optLong("text_tokens", -1);
                long audioIn = incoming.optLong("audio_tokens", -1);
                long textOut = outgoing.optLong("text_tokens", -1);
                long audioOut = outgoing.optLong("audio_tokens", -1);
                long cached = incoming.optLong("cached_tokens", -1);
                JSONObject cachedDetails = incoming.optJSONObject("cached_tokens_details");
                long cachedText = cached == 0 ? 0 : cachedDetails == null ? -1 : cachedDetails.optLong("text_tokens", -1);
                long cachedAudio = cached == 0 ? 0 : cachedDetails == null ? -1 : cachedDetails.optLong("audio_tokens", -1);
                if (textIn + audioIn == input && textOut + audioOut == output && cachedText + cachedAudio == cached) {
                    cost = ApiUsage.estimate(pricing, textIn, audioIn, cachedText, cachedAudio, textOut, audioOut);
                }
            }
        }
        usage.record(response.optString("id"), input, output, cost);
        saveUsage("Suivi de l’appel IA");
        Double costNow = usage.estimatedTotal();
        if (service != null && !costAlerted && AppSettings.costAlert(service) > 0 && costNow != null && costNow >= AppSettings.costAlert(service)) {
            costAlerted = true;
            CallNotices.show(service, 4405, "Seuil de coût estimé atteint", "L’appel continue. Consultez sa fiche pour les détails API.", HistoryActivity.class);
        }
    }

    private void saveUsage(String state) {
        if (service == null || call == null) return;
        String snapshot = java.text.DateFormat.getDateTimeInstance().format(new java.util.Date())
                + "\n" + state + "\nModèle : " + (model.isEmpty() ? "en préparation" : model) + "\n" + usage.summary();
        service.getSharedPreferences("call-metrics", android.content.Context.MODE_PRIVATE).edit()
                .putString("last-usage", snapshot).apply();
        try {
            CallIdentity identity = CallIdentity.read(call);
            new CallStore(service).put(new JSONObject().put("id", localCallId).put("created", created).put("expires", expires)
                    .put("name", identity.name).put("number", identity.number).put("direction", brief.isEmpty() ? "incoming" : "outgoing")
                    .put("mission", brief).put("state", outcome.state()).put("reason", outcome.reason())
                    .put("model", model).put("pricing-date", RealtimeModel.PRICE_DATE).put("usage", usage.summary()));
        } catch (Exception error) { android.util.Log.w("ReponseVoiceCall", "Call history unavailable: " + error.getClass().getSimpleName()); }
    }
}
