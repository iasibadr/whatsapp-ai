package live.pharmaradio.whatsappai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.InCallService;

public class CallBridgeService extends InCallService {
    static CallBridgeService instance;
    static Runnable observer;
    static String lastMessage = "";
    static String lastError = "";
    final Handler main = new Handler(Looper.getMainLooper());
    Call incoming;
    PhoneAgent agent;
    String status = "Appel entrant — choisissez qui répond.";
    private boolean previousMute;
    private boolean automaticPreparation;
    private final Call.Callback callback = new Call.Callback() {
        @Override
        public void onStateChanged(Call call, int state) {
            if (call != incoming) return;
            android.util.Log.i("ReponseVoiceCall", "Call state=" + state);
            if (state == Call.STATE_ACTIVE && agent != null) {
                if (Build.VERSION.SDK_INT >= 29 && call.getDetails().getCallDirection() == Call.Details.DIRECTION_OUTGOING
                        && hasExclusiveCall(call)) setMuted(true);
                agent.attachAudio();
            }
            if (state == Call.STATE_HOLDING && agent != null) release("IA arrêtée : appel mis en attente.", false, true);
            if (state == Call.STATE_DISCONNECTED) {
                android.telecom.DisconnectCause cause = call.getDetails().getDisconnectCause();
                release("Appel terminé côté téléphone (cause " + (cause == null ? "non précisée" : cause.getCode()) + ").");
            }
            changed();
        }
    };
    private final Call.Callback audioGuard = new Call.Callback() {
        @Override
        public void onDetailsChanged(Call call, Call.Details details) {
            if (incoming == null) offerCall(call);
        }

        @Override
        public void onStateChanged(Call call, int state) {
            if (agent != null && call != incoming && state == Call.STATE_ACTIVE) {
                release("IA arrêtée : un autre appel a pris le son téléphonique.", false, true);
            }
        }
    };

    boolean hasExclusiveCall(Call target) {
        for (Call other : getCalls()) {
            if (other != target && other.getState() != Call.STATE_DISCONNECTED) return false;
        }
        return target == incoming;
    }

    boolean mayAnswer(Call target) {
        if (incoming != target || !hasExclusiveCall(target)) return false;
        boolean emergency = target.getDetails().hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
                || target.getDetails().hasProperty(Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL);
        if (emergency) return false;
        return !automaticPreparation || AutoAnswerPolicy.allowed(AppSettings.autoAnswer(this), target.getState() == Call.STATE_RINGING,
                true, !CallIdentity.read(target).number.isEmpty(), false);
    }

    @Override
    public void onCallAdded(Call call) {
        super.onCallAdded(call);
        instance = this;
        call.registerCallback(audioGuard, main);
        offerCall(call);
    }

    private void offerCall(Call call) {
        if (incoming != null && incoming.getState() == Call.STATE_DISCONNECTED) {
            incoming.unregisterCallback(callback);
            release("Appel précédent terminé.");
            incoming = null;
        }
        if (incoming != null || Build.VERSION.SDK_INT < 29) return;
        boolean outgoing = call.getDetails().getCallDirection() == Call.Details.DIRECTION_OUTGOING;
        if (!outgoing && !AppSettings.prefs(this).getBoolean("incoming-enabled", false)) return;
        PreparedMission mission = outgoing ? OutgoingPlan.consume(call.getDetails().getHandle()) : null;
        if (outgoing && mission == null || !outgoing && (call.getState() != Call.STATE_RINGING
                || call.getDetails().getCallDirection() != Call.Details.DIRECTION_INCOMING)) return;
        incoming = call;
        lastError = "";
        android.util.Log.i("ReponseVoiceCall", outgoing ? "Confirmed outgoing AI call attached" : "Incoming call offered; no automatic answer");
        status = outgoing ? "Appel sortant confirmé — préparation de l’IA…" : "Appel entrant — choisissez qui répond.";
        call.registerCallback(callback, main);
        if (outgoing) {
            previousMute = getCallAudioState() != null && getCallAudioState().isMuted();
            agent = new PhoneAgent(this, call, mission);
            agent.prepare();
        }
        changed();
        if (!outgoing && AppSettings.autoAnswer(this)) {
            main.postDelayed(() -> {
                boolean emergency = call.getDetails().hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
                        || call.getDetails().hasProperty(Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL);
                if (incoming == call && agent == null && AutoAnswerPolicy.allowed(AppSettings.autoAnswer(this), call.getState() == Call.STATE_RINGING,
                        hasExclusiveCall(call), !CallIdentity.read(call).number.isEmpty(), emergency)) chooseAI(true);
            }, 20000);
        }
        try {
            startActivity(new Intent(this, CallMenuActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException error) {
            status = "Ouvrez la notification WhatsApp AI pour choisir.";
            changed();
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        call.unregisterCallback(audioGuard);
        if (call == incoming) {
            call.unregisterCallback(callback);
            release("Appel terminé.");
            incoming = null;
            changed();
        }
        super.onCallRemoved(call);
    }

    void chooseHuman() {
        if (incoming == null) return;
        release("Vous parlez directement au correspondant.", false, true);
        if (incoming.getState() == Call.STATE_RINGING) incoming.answer(0);
        changed();
    }

    void chooseAI() {
        chooseAI(false);
    }

    private void chooseAI(boolean automatic) {
        if (incoming == null || incoming.getState() != Call.STATE_RINGING || agent != null) return;
        if (incoming.getDetails().hasProperty(Call.Details.PROPERTY_EMERGENCY_CALLBACK_MODE)
                || incoming.getDetails().hasProperty(Call.Details.PROPERTY_NETWORK_IDENTIFIED_EMERGENCY_CALL)) {
            status = "Appel identifié comme urgence : répondez personnellement dans Téléphone.";
            changed();
            return;
        }
        if (!hasExclusiveCall(incoming)) {
            status = "Double appel : utilisez Téléphone. L’IA ne doit pas interrompre votre autre conversation.";
            changed();
            return;
        }
        previousMute = getCallAudioState() != null && getCallAudioState().isMuted();
        automaticPreparation = automatic;
        agent = new PhoneAgent(this, incoming);
        status = "Préparation de l’IA — l’appel n’est pas encore décroché…";
        changed();
        agent.prepare();
    }

    void hangUp() {
        hangUp("Appel terminé manuellement.");
    }

    void hangUp(String message) {
        Call call = incoming;
        release(message);
        if (call != null) {
            if (call.getState() == Call.STATE_RINGING) call.reject(false, null);
            else if (call.getState() != Call.STATE_DISCONNECTED) call.disconnect();
        }
        changed();
    }

    void release(String message) {
        release(message, false);
    }

    void release(String message, boolean failure) {
        release(message, failure, false);
    }

    void release(String message, boolean failure, boolean handoff) {
        if (agent != null) {
            PhoneAgent previous = agent;
            agent = null;
            previous.finish(message, failure, handoff);
            setMuted(previousMute);
        }
        automaticPreparation = false;
        status = message;
        changed();
    }

    void changed() {
        if (observer != null) observer.run();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (incoming == null || incoming.getState() == Call.STATE_DISCONNECTED) {
            manager.cancel(4401);
            return;
        }
        NotificationChannel channel = new NotificationChannel("real-calls", "Choix sur les appels", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);
        PendingIntent menu = PendingIntent.getActivity(this, 0, new Intent(this, CallMenuActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder notification = new Notification.Builder(this, "real-calls")
                .setSmallIcon(android.R.drawable.sym_call_incoming).setContentTitle("WhatsApp AI — appel en cours")
                .setContentText(status).setCategory(Notification.CATEGORY_CALL).setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setContentIntent(menu);
        if (incoming.getState() == Call.STATE_RINGING) notification.setFullScreenIntent(menu, true);
        notification.addAction(new Notification.Action.Builder(null, "Répondre", action("human", 1)).build());
        if (incoming.getState() == Call.STATE_RINGING && agent == null) {
            notification.addAction(new Notification.Action.Builder(null, "Confier à l’IA", action("ai", 2)).build());
        }
        notification.addAction(new Notification.Action.Builder(null, "Raccrocher", action("hangup", 3)).build());
        try { manager.notify(4401, notification.build()); }
        catch (RuntimeException error) { android.util.Log.w("ReponseVoiceCall", "Call notification unavailable"); }
    }

    private PendingIntent action(String action, int code) {
        return PendingIntent.getBroadcast(this, code, new Intent(this, CallActionReceiver.class).setAction(action), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onDestroy() {
        for (Call call : getCalls()) call.unregisterCallback(audioGuard);
        if (incoming != null) incoming.unregisterCallback(callback);
        incoming = null;
        release("Service d’appel arrêté.", true);
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
