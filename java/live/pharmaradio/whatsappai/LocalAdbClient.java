package live.pharmaradio.whatsappai;

import android.content.Context;
import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbStream;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.concurrent.TimeUnit;

final class LocalAdbClient extends AbsAdbConnectionManager {
    private final LocalAdbIdentity identity;

    LocalAdbClient(Context context) throws Exception {
        identity = LocalAdbIdentity.load(context);
        setApi(android.os.Build.VERSION.SDK_INT);
        setHostAddress("127.0.0.1");
        setTimeout(12, TimeUnit.SECONDS);
        setThrowOnUnauthorised(true);
    }

    @Override protected PrivateKey getPrivateKey() { return identity.privateKey; }
    @Override protected Certificate getCertificate() { return identity.certificate; }
    @Override protected String getDeviceName() { return "WhatsApp Relay Lab"; }

    boolean pairLocal(int port, String code) throws Exception {
        if (!LocalActivationPolicy.freshPort(port, 0, 0) || !LocalActivationPolicy.pairingCode(code)) throw new IllegalArgumentException("Association invalide");
        return pair("127.0.0.1", port, code);
    }

    boolean activateLocal(Context context, int port) throws Exception {
        if (!LocalActivationPolicy.freshPort(port, 0, 0)) throw new IllegalArgumentException("Service local invalide");
        if (!connect("127.0.0.1", port)) throw new IllegalStateException("Connexion locale refusée");
        String command = LocalActivationPolicy.activationCommand(context.getApplicationInfo().sourceDir);
        try (AdbStream stream = openStream("shell:" + command)) {
            java.io.InputStream input = stream.openInputStream();
            java.io.ByteArrayOutputStream result = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (result.size() + count > 8192) throw new IllegalStateException("Réponse locale trop longue");
                result.write(buffer, 0, count);
                if (result.toString(java.nio.charset.StandardCharsets.UTF_8.name()).contains("VOICE_LOCAL_ACTIVATED")) return true;
            }
            return result.toString(java.nio.charset.StandardCharsets.UTF_8.name()).contains("VOICE_LOCAL_ACTIVATED");
        } finally { disconnect(); }
    }
}
