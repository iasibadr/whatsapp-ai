package live.pharmaradio.whatsappai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import io.github.muntashirakon.adb.android.AdbMdns;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.concurrent.atomic.AtomicBoolean;

public class LocalActivationService extends Service {
    private static final String CHANNEL = "local-activation";
    private static final int NOTICE = 6501;
    private static volatile LocalActivationService instance;
    private static volatile String lastStatus = "";
    private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
    private final AtomicBoolean ended = new AtomicBoolean();
    private final AtomicBoolean working = new AtomicBoolean();
    private volatile int port;
    private volatile long seen;
    private boolean pairing;
    private boolean initialized;
    private AdbMdns discovery;
    private volatile LocalAdbClient client;

    static boolean paired(Context context) {
        return context.getSharedPreferences("local-activation", MODE_PRIVATE).getBoolean("paired", false);
    }

    static boolean running() { return instance != null && !instance.ended.get(); }
    static String status() { return lastStatus; }
    static boolean activatedLocally(Context context) { return context.getSharedPreferences("local-activation", MODE_PRIVATE).getBoolean("activated-locally", false); }

    static void start(Context context, boolean pairing) {
        if (running()) return;
        lastStatus = "";
        context.startForegroundService(new Intent(context, LocalActivationService.class).setAction(pairing ? "pair" : "activate"));
    }

    static void submit(String code) {
        LocalActivationService service = instance;
        if (service == null || service.ended.get() || !service.pairing) return;
        service.main.post(() -> service.pair(code));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || "cancel".equals(intent.getAction())) { finish("Configuration interrompue. Aucun appel passé."); return START_NOT_STICKY; }
        if (initialized) return START_NOT_STICKY;
        initialized = true;
        pairing = "pair".equals(intent.getAction());
        instance = this;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Association de ce téléphone", NotificationManager.IMPORTANCE_DEFAULT));
        lastStatus = pairing ? "Ouvrez Débogage sans fil → Associer avec un code, puis utilisez la notification WhatsApp AI." : "Recherche du service local de ce téléphone…";
        if (android.os.Build.VERSION.SDK_INT >= 34) startForeground(NOTICE, notification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE);
        else startForeground(NOTICE, notification());
        main.postDelayed(() -> finish("Délai dépassé. Revenez dans l’application pour réessayer ; aucun appel passé."), 150000);
        if (!notifications.areNotificationsEnabled()) { finish("Autorisez les notifications pour saisir le code sur ce téléphone."); return START_NOT_STICKY; }
        try {
            discovery = new AdbMdns(this, pairing ? AdbMdns.SERVICE_TYPE_TLS_PAIRING : AdbMdns.SERVICE_TYPE_TLS_CONNECT,
                    (host, found) -> main.post(() -> discovered(host, found)));
            discovery.start();
        } catch (RuntimeException error) { finish("Recherche locale indisponible. Vérifiez le Wi-Fi et le débogage sans fil."); }
        return START_NOT_STICKY;
    }

    private void discovered(InetAddress host, int found) {
        if (ended.get()) return;
        try {
            if (host == null || NetworkInterface.getByInetAddress(host) == null) return;
        } catch (java.net.SocketException error) { return; }
        port = found;
        seen = SystemClock.elapsedRealtime();
        if (!LocalActivationPolicy.freshPort(port, seen, seen)) return;
        if (pairing) update("Code Android détecté. Dans cette notification, touchez « Entrer le code » et saisissez les 6 chiffres.");
        else activate();
    }

    private void pair(String code) {
        if (!LocalActivationPolicy.pairingCode(code)) { update("Saisissez uniquement les 6 chiffres affichés par Android."); return; }
        if (!LocalActivationPolicy.freshPort(port, seen, SystemClock.elapsedRealtime())) {
            update("Ouvrez à nouveau « Associer avec un code » dans Android, puis utilisez cette notification."); return;
        }
        if (!working.compareAndSet(false, true)) return;
        int selected = port;
        update("Association locale en cours…");
        new Thread(() -> {
            try {
                client = new LocalAdbClient(this);
                boolean success = client.pairLocal(selected, code);
                main.post(() -> {
                    if (ended.get()) return;
                    if (success && getSharedPreferences("local-activation", MODE_PRIVATE).edit()
                            .putBoolean("paired", true).putBoolean("activate-on-return", true).commit()) {
                        finish("Téléphone associé. Revenez dans WhatsApp AI : le relais s'activera automatiquement.");
                    } else finish("Association non enregistrée. Réessayez depuis l’application.");
                });
            } catch (Exception error) {
                main.post(() -> finish("Association refusée ou expirée. Ouvrez un nouveau code Android puis réessayez depuis l’application."));
            } finally { disconnect(); }
        }, "voice-local-pairing").start();
    }

    private void activate() {
        if (!LocalActivationPolicy.freshPort(port, seen, SystemClock.elapsedRealtime()) || !working.compareAndSet(false, true)) return;
        int selected = port;
        update("Activation locale de l’assistant… Aucun appel n’est composé.");
        main.postDelayed(() -> finish("Activation non confirmée dans le délai prévu. Vérifiez l’état dans l’application avant de réessayer."), 30000);
        new Thread(() -> {
            try {
                if (getSystemService(android.media.AudioManager.class).getMode() != android.media.AudioManager.MODE_NORMAL) throw new IllegalStateException("Audio occupé");
                client = new LocalAdbClient(this);
                boolean success = client.activateLocal(this, selected);
                for (int attempt = 0; attempt < 40 && !ended.get() && BridgeProvider.getHelper() == null; attempt++) Thread.sleep(200);
                main.post(() -> {
                    if (ended.get()) return;
                    boolean confirmed = success && BridgeProvider.getHelper() != null;
                    if (confirmed) confirmed = getSharedPreferences("local-activation", MODE_PRIVATE).edit().putBoolean("activated-locally", true).commit();
                    finish(confirmed ? "Assistant activé. Aucun appel passé."
                            : "Activation non confirmée. Vérifiez le débogage sans fil et terminez toute communication avant de réessayer.");
                });
            } catch (Exception error) {
                String category = error.getClass().getSimpleName();
                main.post(() -> finish("Connexion locale refusée ou interrompue (" + category + "). Réessayez ; si nécessaire, refaites l’association de ce téléphone."));
            } finally { disconnect(); }
        }, "voice-local-activation").start();
    }

    private void disconnect() {
        LocalAdbClient connection = client;
        if (connection != null) {
            try { connection.disconnect(); } catch (Exception ignored) { }
        }
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 6502, new Intent(this, SetupActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 6503, new Intent(this, LocalActivationService.class).setAction("cancel"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle(pairing ? "Associer ce téléphone" : "Activer WhatsApp AI")
                .setContentText(lastStatus).setStyle(new Notification.BigTextStyle().bigText(lastStatus))
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null, "Annuler", cancel).build());
        if (pairing && !working.get()) {
            PendingIntent input = PendingIntent.getBroadcast(this, 6504, new Intent(this, LocalPairingReceiver.class).setAction("pair-code"),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            builder.addAction(new Notification.Action.Builder(null, "Entrer le code", input)
                    .addRemoteInput(new RemoteInput.Builder("pairing-code").setLabel("Les 6 chiffres affichés par Android").build())
                    .setAllowGeneratedReplies(false).build());
        }
        return builder.build();
    }

    private void update(String message) {
        if (ended.get()) return;
        if (message.equals(lastStatus)) return;
        lastStatus = message;
        getSystemService(NotificationManager.class).notify(NOTICE, notification());
    }

    private void finish(String message) {
        if (!ended.compareAndSet(false, true)) return;
        lastStatus = message;
        stopForeground(STOP_FOREGROUND_REMOVE);
        PendingIntent reopen = PendingIntent.getActivity(this, 6507, new Intent(this, SetupActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        getSystemService(NotificationManager.class).notify(6506, new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Configuration WhatsApp AI")
                .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(reopen)
                .setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(new Notification.Action.Builder(null, "Revenir au guide", reopen).build()).build());
        stopSelf();
    }

    @Override public void onTimeout(int startId) { finish("Configuration interrompue après le délai Android. Réessayez depuis l’application."); }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        ended.set(true);
        main.removeCallbacksAndMessages(null);
        if (discovery != null) {
            try { discovery.stop(); } catch (RuntimeException ignored) { }
        }
        if (instance == this) instance = null;
        NetworkCleanup.start(this::disconnect);
        super.onDestroy();
    }
}
