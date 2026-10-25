package live.pharmaradio.whatsappai;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

public final class SetupActivity extends Activity {
    static volatile boolean visible;
    private TextView output;
    private String report = "";
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean audioTestActive;
    private volatile PcmTransportTest pcmTest;
    private volatile AiTrial aiTrial;
    private final Object audioControl = new Object();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { activateAfterPairing();output.setText(LocalActivationService.status() + "\n\n" + report); ui.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout panel = Ui.page(this,"Configuration","Préparer ce téléphone pour les appels WhatsApp avec IA");
        LinearLayout introduction = Ui.card(panel,"Première installation","Une fois la clé et l’association locale configurées, l’application active elle-même son relais.");
        button(introduction,"Voir les étapes d’installation",()->startActivity(new Intent(this,InstallationGuideActivity.class)));
        Ui.text(introduction,"Chaque téléphone utilise sa propre clé OpenAI. Le code d’association se saisit uniquement sur ce téléphone, hors appel.",16)
                .setTextColor(Ui.MUTED);
        button(introduction,"Configurer cet appareil",()->{
            if(!KeyVault.configured(this)){startActivity(new Intent(this,CredentialsActivity.class));return;}
            if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=0){
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},2);return;
            }
            if(!LocalActivationService.paired(this)){
                LocalActivationService.start(this,true);
                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
            }else LocalActivationService.start(this,false);
        });
        button(introduction,"Clé OpenAI",()->startActivity(new Intent(this,CredentialsActivity.class)));
        LinearLayout diagnostics = Ui.card(panel,"Diagnostics avancés","À utiliser si la configuration ne se termine pas ou si un test échoue.");
        diagnostics.setVisibility(android.view.View.GONE);
        Ui.button(panel,"Afficher les diagnostics avancés",false,()->
                diagnostics.setVisibility(diagnostics.getVisibility()==android.view.View.VISIBLE
                        ? android.view.View.GONE : android.view.View.VISIBLE));
        panel.removeView(diagnostics);
        panel.addView(diagnostics);
        button(diagnostics,"Autoriser les notifications",()->requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},1));
        button(diagnostics,"Associer cette application au téléphone",()->{
            LocalActivationService.start(this,true);startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        });
        button(diagnostics,"Activer le relais local",()->LocalActivationService.start(this,false));
        button(diagnostics,"Vérifier le canal privé",()->test(false));
        button(diagnostics,"Dernier contrôle d’appel sans numéro",()->{
            try{IBinder helper=BridgeProvider.getHelper();report=helper==null?"Relais local absent.":audioCommand(19);}
            catch(Exception error){report="Contrôle indisponible.";}
        });
        button(diagnostics,"Tester le service d’appel sans appel",()->{
            CallSessionService.selfTestCompleted=false;
            startForegroundService(new Intent(this,CallSessionService.class).setAction("selftest"));
            ui.postDelayed(()->report=CallSessionService.selfTestCompleted
                    ? "Service d’appel démarré au premier plan sans appel."
                    : "Démarrage du service non confirmé.",2500);
        });
        button(diagnostics,"Tester la présentation IA sans appel",()->{
            if(!busy.compareAndSet(false,true))return;
            report="Présentation IA : test réseau sans appel ni lecture audio en cours…";
            new Thread(()->{
                RealtimeAudio session=null;String result;String stage="préparation";
                try{
                    if(getSystemService(android.media.AudioManager.class).getMode()!=android.media.AudioManager.MODE_NORMAL)
                        throw new IllegalStateException("Appel actif");
                    session=new RealtimeAudio(true,true);
                    session.mission(this,new PreparedMission("Présente-toi comme assistant IA et vérifie que la voix est claire.",MissionDossier.empty()),"+10000000000");
                    stage="connexion";session.connect(this);stage="présentation";session.greeting();
                    result="Présentation IA prête, " + session.output.size() + " octets PCM générés, " + session.outputTranscriptions.get() + " transcription(s) de sortie ; aucun appel ni lecture audio.";
                }catch(Exception error){String detail=error.getMessage();if(detail==null||!detail.matches("[A-Za-z0-9_ -]{1,120}"))detail="détail indisponible";
                    result="Présentation IA refusée à l’étape "+stage+" ("+error.getClass().getSimpleName()+" : "+detail+"). Aucun appel ni audio transmis.";}
                finally{if(session!=null)session.close();busy.set(false);}
                String finalResult=result;ui.post(()->report=finalResult);
            },"greeting-without-call").start();
        });
        button(diagnostics,"Tester le journal texte sans appel",()->{
            try{report=TranscriptStore.selfTest(this)?"Journal texte local : lecture, ordre, persistance et suppression validés sans appel.":"Journal texte local : échec du contrôle.";}
            catch(Exception error){report="Journal texte local indisponible : "+error.getClass().getSimpleName();}
        });
        button(diagnostics,"Tester la transcription sans appel",()->{
            if(!busy.compareAndSet(false,true))return;
            report="Transcription : test réseau avec voix synthétique, sans appel ni lecture audio…";
            new Thread(()->{
                RealtimeAudio session=null;String result;
                try{
                    if(getSystemService(android.media.AudioManager.class).getMode()!=android.media.AudioManager.MODE_NORMAL)
                        throw new IllegalStateException("Appel actif");
                    session=new RealtimeAudio(true,true);
                    session.mission(this,new PreparedMission("Essai de transcription sans appel.",MissionDossier.empty()),"+10000000000");
                    session.connect(this);session.greeting();
                    byte[] frame=new byte[960];long next=android.os.SystemClock.elapsedRealtime();
                    while(session.output.size()>0){
                        int count=session.output.drain(frame);
                        if(count>0)session.append(java.util.Arrays.copyOf(frame,count));
                        next+=20;long delay=next-android.os.SystemClock.elapsedRealtime();if(delay>0)Thread.sleep(delay);
                    }
                    java.util.Arrays.fill(frame,(byte)0);
                    for(int i=0;i<55;i++){session.append(frame);next+=20;long delay=next-android.os.SystemClock.elapsedRealtime();if(delay>0)Thread.sleep(delay);}
                    RealtimeAudio activeSession=session;activeSession.await(()->activeSession.inputTranscriptions.get()>0,18000);
                    result="Transcription entrante validée avec une voix synthétique ; aucun appel, aucune lecture ni conservation de voix.";
                }catch(Exception error){result="Transcription sans appel non validée ("+error.getClass().getSimpleName()+").";}
                finally{if(session!=null)session.close();busy.set(false);}
                String finalResult=result;ui.post(()->report=finalResult);
            },"transcript-loopback-no-call").start();
        });
        button(diagnostics,"Diagnostiquer l’appel sans audio",this::diagnoseOwner);
        button(diagnostics,"Dernier rapport",()->report=AiTrial.saved(this));
        output = Ui.text(Ui.card(panel,"État de ce téléphone","Le résultat des contrôles apparaît ici."),"Vérification…",15);
        output.setTextIsSelectable(true);
        if (state != null) report = state.getString("report", "");
    }
    private void button(LinearLayout panel, String text, Runnable action) {
        Ui.button(panel,text,text.equals("Configurer cet appareil"),action);
    }
    private void activateAfterPairing() {
        if(!visible || LocalActivationService.running())return;
        android.content.SharedPreferences settings=getSharedPreferences("local-activation",MODE_PRIVATE);
        if(!settings.getBoolean("activate-on-return",false))return;
        if(BridgeProvider.getHelper()!=null){settings.edit().putBoolean("activate-on-return",false).apply();return;}
        if(settings.edit().putBoolean("activate-on-return",false).commit())LocalActivationService.start(this,false);
    }
    private String audioCommand(int code) throws Exception {
        IBinder binder = BridgeProvider.getHelper();
        if (binder == null) throw new IOException("Relais absent");
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(BridgeProvider.DESCRIPTOR);
            if (code == 5) request.writeString("whatsapp-eight-second-test-consented");
            if (!binder.transact(code, request, reply, 0)) throw new IOException("Relais incompatible : arrêter puis réactiver");
            reply.readException();
            return code == 6 || code == 1 || code == 8 || code == 19 ? reply.readString() : "";
        } finally { request.recycle(); reply.recycle(); }
    }
    private void diagnoseOwner() {
        if (!busy.compareAndSet(false, true)) return;
        new Thread(() -> {
            String value;
            try {
                JSONObject helper = new JSONObject(audioCommand(1));
                if (!"0.22-pcm-transport".equals(helper.optString("version")))
                    throw new IOException("Ancien relais : arrêter et réactiver HORS APPEL, puis recommencer");
                value = new JSONObject(audioCommand(8)).toString(2);
            } catch (Exception e) { value = "Diagnostic propriétaire non disponible : " + e.getClass().getSimpleName() + " — " + e.getMessage(); }
            final String result = value;
            ui.post(() -> { report = result; busy.set(false); });
        }, "read-only-audio-owner").start();
    }
    private void audioTest() {
        if (!busy.compareAndSet(false, true)) return;
        audioTestActive = true;
        new Thread(() -> {
            try {
                JSONObject helper = new JSONObject(audioCommand(1));
                if (!"0.22-pcm-transport".equals(helper.optString("version"))) throw new IOException("Ancien relais actif : touchez Arrêter le relais, puis Activer avant l’appel");
                synchronized (audioControl) {
                    if (!audioTestActive) return;
                    audioCommand(5);
                }
                long limit = SystemClock.elapsedRealtime() + 15000;
                while (audioTestActive && SystemClock.elapsedRealtime() < limit) {
                    String value = audioCommand(6);
                    JSONObject status = new JSONObject(value);
                    ui.post(() -> report = value);
                    String state = status.optString("state");
                    if (!"running".equals(state) && !"starting".equals(state)) return;
                    Thread.sleep(300);
                }
                audioCommand(7);
            } catch (Exception e) {
                String value = "Test non validé : " + e.getMessage();
                ui.post(() -> report = value);
            } finally { audioTestActive = false; busy.set(false); }
        }, "audio-test-control").start();
    }
    private void cancelAudio() {
        AiTrial ai=aiTrial;if(ai!=null)ai.cancel();
        AutonomyBench.cancelPreparation();
        PcmTransportTest pcm=pcmTest;
        if(pcm!=null) pcm.cancel();
        if (!audioTestActive) return;
        audioTestActive = false;
        new Thread(() -> { synchronized (audioControl) { try { audioCommand(7); } catch (Exception ignored) {} } }, "cancel-audio-test").start();
    }
    private void pcmTest() {
        pcmTest(false);
    }
    private void aiTest(boolean synthetic){
        aiTest(synthetic,false,0);
    }
    private void confirmContinuous(int delay){
        new AlertDialog.Builder(this).setTitle("Conversation IA consentie — arrêt manuel possible")
            .setMessage("Appel WhatsApp déjà décroché et correspondant informé : sa voix sera envoyée à OpenAI pour répondre automatiquement. Il peut interrompre la réponse. Aucun enregistrement local. Restez sur cet écran. "+(delay==20?"Le départ aura lieu dans20secondes pour déconnecter ADB.":"Départ immédiat."))
            .setNegativeButton("Annuler",null).setPositiveButton("Démarrer la conversation",(d,w)->aiTest(false,true,delay)).show();
    }
    private void aiTest(boolean synthetic,boolean continuous,int delay){
        if(!busy.compareAndSet(false,true))return;
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        AiTrial trial=new AiTrial();aiTrial=trial;
        new Thread(()->{
            try{trial.run(this,synthetic,continuous,delay,value->ui.post(()->report=value));}
            finally{aiTrial=null;busy.set(false);ui.post(()->getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));}
        },"apk-ai-trial").start();
    }
    private void pcmTest(boolean voice) {
        if(!busy.compareAndSet(false,true)) return;
        PcmTransportTest test=new PcmTransportTest(); pcmTest=test;
        report="Transport PCM en cours (30 secondes maximum)…";
        new Thread(() -> {
            try {
                JSONObject helper=new JSONObject(audioCommand(1));
                if(!"0.22-pcm-transport".equals(helper.optString("version"))) throw new IOException("Réactivez le relais v0.22 hors appel");
                IBinder binder=BridgeProvider.getHelper();
                if(binder==null) throw new IOException("Relais absent");
                byte[] speech=null;
                try{
                    test.run(binder,value -> ui.post(() -> report=value),speech);
                }finally{if(speech!=null)java.util.Arrays.fill(speech,(byte)0);}
            } catch(Exception e) { ui.post(() -> report="Transport non validé : "+e.getMessage()); }
            finally { pcmTest=null; busy.set(false); }
        },"apk-pcm-test").start();
    }
    private void test(boolean stop) {
        if (!busy.compareAndSet(false,true)) return;
        new Thread(() -> {
            String result;
            String stage = "helper_lookup";
            JSONObject status = new JSONObject();
            try {
                IBinder binder = BridgeProvider.getHelper();
                if (binder == null) throw new IllegalStateException("Relais absent : activez-le d’abord");
                stage = "helper_status";
                Parcel request = Parcel.obtain(), reply = Parcel.obtain();
                try {
                    request.writeInterfaceToken(BridgeProvider.DESCRIPTOR);
                    if (!binder.transact(stop ? 4 : 1, request, reply, 0)) throw new IOException("Transaction refusée");
                    reply.readException();
                    status = stop ? new JSONObject().put("stop_requested",true) : new JSONObject(reply.readString());
                } finally { request.recycle(); reply.recycle(); }
                if (!stop) {
                    status.put("diagnostic_app_version", "0.22-pcm-transport");
                    stage = "channel_descriptor";
                    request = Parcel.obtain(); reply = Parcel.obtain();
                    ParcelFileDescriptor descriptor;
                    try {
                        request.writeInterfaceToken(BridgeProvider.DESCRIPTOR);
                        if (!binder.transact(3, request, reply, 0)) throw new IOException("Canal refusé");
                        reply.readException(); descriptor = reply.readTypedObject(ParcelFileDescriptor.CREATOR);
                    } finally { request.recycle(); reply.recycle(); }
                    if (descriptor == null) throw new IOException("Canal absent");
                    try (ParcelFileDescriptor fd = descriptor) {
                        stage = "channel_roundtrip_poll";
                        ChannelProbe.verify(fd.getFileDescriptor());
                        status.put("private_channel_roundtrip",true);
                    }
                }
                result = status.toString(2);
            } catch (Exception error) {
                try {
                    status.put("diagnostic_valid", false).put("failed_stage", stage)
                        .put("error_type", error.getClass().getSimpleName()).put("error", error.getMessage());
                    result = status.toString(2);
                } catch (Exception ignored) { result = "Diagnostic non validé : " + stage + " / " + error.getClass().getSimpleName(); }
            }
            final String value = result;
            ui.post(() -> { report = value; busy.set(false); });
        },"relay-check").start();
    }
    @Override protected void onResume() {
        super.onResume();visible=true; ui.post(refresh);
        activateAfterPairing();
    }
    @Override protected void onPause() { visible=false;AutonomyBench.cancelPreparation();cancelAudio(); ui.removeCallbacks(refresh); super.onPause(); }
    @Override public void onSaveInstanceState(Bundle state) { state.putString("report", report); super.onSaveInstanceState(state); }
}
