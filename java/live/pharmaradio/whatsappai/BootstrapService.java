package live.pharmaradio.whatsappai;

import android.content.Context;
import android.os.Binder;
import android.os.Parcel;
import android.os.Process;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class BootstrapService extends Binder {
    static final String DESCRIPTOR = "live.pharmaradio.whatsappai.Bootstrap";
    private static final String PACKAGE = "live.pharmaradio.whatsappai";
    private final Context context;
    private final int appUid;

    public BootstrapService(Context context) {
        this.context = context;
        this.appUid = context.getApplicationInfo().uid;
    }

    @Override
    protected synchronized boolean onTransact(int code, Parcel request, Parcel reply, int flags) {
        if (code == 16777115 && Binder.getCallingUid() == 2000) {
            System.exit(0);
            return true;
        }
        if (Binder.getCallingUid() != appUid || Process.myUid() != 2000) throw new SecurityException("Application propriétaire et shell requis");
        request.enforceInterface(DESCRIPTOR);
        if (code != 1) return false;
        long identity = Binder.clearCallingIdentity();
        try {
            String result = activate(context);
            reply.writeNoException();
            reply.writeString(result);
        } catch (Exception error) {
            reply.writeException(new IllegalStateException("Activation refusée ou interrompue (" + error.getClass().getSimpleName() + "). Terminez tout appel et réessayez."));
        } finally { Binder.restoreCallingIdentity(identity); }
        return true;
    }

    static String activate(Context context) throws Exception {
        String audio = command(true, "/system/bin/dumpsys", "audio");
        if (!audio.contains("- Actual mode = MODE_NORMAL")) throw new IllegalStateException("Audio actif");
        String beforeDialer = command(true, "/system/bin/cmd", "role", "get-role-holders", "android.app.role.DIALER");
        String beforeCompanions = companions();
        String existing = command(false, "/system/bin/pidof", "voice-autonome-audio").trim();
        if (existing.isEmpty()) {
            File source = new File(context.getPackageManager().getApplicationInfo(PACKAGE, 0).sourceDir).getCanonicalFile();
            if (!source.getPath().startsWith("/data/app/") || !source.isFile()) throw new SecurityException("APK installée introuvable");
            ProcessBuilder process = new ProcessBuilder("/system/bin/nohup", "/system/bin/app_process", "/", "--nice-name=voice-autonome-audio", PACKAGE + ".ShellAudioBridge");
            process.environment().put("CLASSPATH", source.getPath());
            process.redirectInput(new File("/dev/null"));
            process.redirectOutput(ProcessBuilder.Redirect.appendTo(new File("/data/local/tmp/voice-autonome-audio.log")));
            process.redirectErrorStream(true);
            process.start();
            for (int attempt = 0; attempt < 30 && command(false, "/system/bin/pidof", "voice-autonome-audio").trim().isEmpty(); attempt++) Thread.sleep(100);
            if (command(false, "/system/bin/pidof", "voice-autonome-audio").trim().isEmpty()) throw new IllegalStateException("Helper non démarré");
        }
        command(true, "/system/bin/appops", "set", PACKAGE, "MANAGE_ONGOING_CALLS", "allow");
        command(true, "/system/bin/cmd", "telecom", "add-or-remove-call-companion-app", PACKAGE, "1");
        if (!beforeDialer.equals(command(true, "/system/bin/cmd", "role", "get-role-holders", "android.app.role.DIALER"))) {
            throw new IllegalStateException("Application Téléphone modifiée ; vérifier Android");
        }
        String after = companions();
        for (String entry : beforeCompanions.split("\\s+")) {
            if (!entry.isEmpty() && !(" " + after + " ").contains(" " + entry + " ")) throw new IllegalStateException("Compagnon existant absent");
        }
        return "Service demandé depuis ce téléphone. Vérifiez maintenant le canal audio et OpenAI ; aucun appel n’a été passé.";
    }

    private static String companions() throws Exception {
        StringBuilder result = new StringBuilder();
        for (String line : command(true, "/system/bin/dumpsys", "telecom").split("\n")) {
            if (line.contains("DefaultCallCompanionApps:")) {
                java.util.regex.Matcher names = java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(?:\\.[a-zA-Z0-9_]+){2,}").matcher(line);
                while (names.find()) result.append(names.group()).append(' ');
            }
        }
        return result.toString().trim();
    }

    private static String command(boolean requireSuccess, String... arguments) throws Exception {
        java.lang.Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (java.io.InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (output.size() + count > 2000000) { process.destroy(); return; }
                    output.write(buffer, 0, count);
                }
            } catch (Exception ignored) { }
        }, "bootstrap-result");
        reader.start();
        if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroy(); throw new IllegalStateException("Délai dépassé"); }
        reader.join(1000);
        if (reader.isAlive() || requireSuccess && process.exitValue() != 0) throw new IllegalStateException("Commande refusée");
        return output.toString(StandardCharsets.UTF_8.name()).trim();
    }
}
