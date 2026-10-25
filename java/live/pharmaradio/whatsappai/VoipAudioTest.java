package live.pharmaradio.whatsappai;

import android.content.Context;
import android.media.*;
import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Eight-second local signal test. No speech retained, external network or call control. */
final class VoipAudioTest {
    private final Context context;
    private final AudioManager manager;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final AtomicBoolean cancel = new AtomicBoolean();
    private volatile String report = "{\"state\":\"idle\",\"whatsapp_bidirectional_verified\":false}";
    private volatile JSONObject lastOwnerDiagnostic = new JSONObject();
    private volatile String sessionKey="";
    String sessionKey() { return sessionKey; }
    VoipAudioTest(Context context, AudioManager manager) { this.context = context; this.manager = manager; }
    String status() { return report; }
    void stop() { cancel.set(true); }
    boolean running() { return busy.get(); }
    void start() {
        if (!busy.compareAndSet(false, true)) throw new IllegalStateException("Test déjà actif");
        cancel.set(false);
        report = "{\"state\":\"starting\",\"whatsapp_bidirectional_verified\":false}";
        new Thread(this::run, "whatsapp-audio-test").start();
    }
    private void guard() throws Exception {
        JSONObject observed = inspectOwner();
        lastOwnerDiagnostic = observed;
        if (observed.getInt("audio_manager_mode") != AudioManager.MODE_IN_COMMUNICATION) throw new IllegalStateException("Aucun appel VoIP actif, ou appel SIM : test refusé");
        if (!observed.getBoolean("strict_policy_allows")) throw new IllegalStateException("Propriétaire audio WhatsApp non confirmé : test refusé");
    }
    JSONObject inspectOwner() throws Exception {
        int uid = context.getPackageManager().getApplicationInfo("com.whatsapp", 0).uid;
        int before = manager.getMode();
        String telecomBefore = serviceDump("telecom");
        String audio = serviceDump("audio");
        String telecomAfter = serviceDump("telecom");
        int after = manager.getMode();
        JSONObject observed = new JSONObject(TelecomCallPolicy.diagnostics(audio, telecomBefore, telecomAfter, uid));
        if (before != AudioManager.MODE_IN_COMMUNICATION || after != before) observed.put("strict_policy_allows", false);
        sessionKey=observed.getBoolean("strict_policy_allows") ? TelecomCallPolicy.sessionKey(telecomBefore,telecomAfter) : "";
        return observed.put("diagnostic", "owner-v0.22").put("recording_routes",RouteDiagnostics.recordings(context,manager)).put("audio_manager_mode", before)
            .put("no_current_calls_verified",TelecomCallPolicy.noCalls(telecomBefore) && TelecomCallPolicy.noCalls(telecomAfter))
            .put("audio_manager_mode_after", after)
            .put("audio_started_by_diagnostic", false).put("audio_test_already_running", busy.get());
    }
    boolean observeLaunch(LaunchAttempt attempt) throws Exception {
        String before=serviceDump("telecom"),after=serviceDump("telecom");
        return TelecomCallPolicy.observeLaunch(attempt,before,after,SystemClock.elapsedRealtime());
    }
    private String serviceDump(String service) throws Exception {
        if (!"audio".equals(service) && !"telecom".equals(service)) throw new IllegalArgumentException("Service non autorisé");
        java.lang.Process p = new ProcessBuilder("/system/bin/dumpsys", service).redirectErrorStream(true).start();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        AtomicBoolean overflow = new AtomicBoolean();
        Thread reader = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] b = new byte[4096]; int n;
                while ((n = in.read(b)) != -1) {
                    if (result.size() + n > 2000000) { overflow.set(true); p.destroy(); return; }
                    result.write(b, 0, n);
                }
            } catch (IOException ignored) { }
        }, "audio-owner-check");
        reader.start();
        try {
            if (!p.waitFor(2, TimeUnit.SECONDS)) throw new IOException("Vérification audio trop lente");
            reader.join(200);
            if (reader.isAlive() || overflow.get() || p.exitValue() != 0) throw new IOException("État audio indisponible");
            return result.toString("UTF-8");
        } finally { p.destroy(); }
    }
    private void run() {
        lastOwnerDiagnostic = new JSONObject();
        AudioRecord record = null; AudioTrack track = null;
        short[] incoming = new short[480], outgoing = new short[480];
        String stage = "verify_whatsapp_owner", outcome = "failed", error = "";
        long samples = 0, written = 0; int peak = 0; double energy = 0;
        boolean captureStarted = false, playbackStarted = false;
        AtomicBoolean finished = new AtomicBoolean();
        // Fatal stall only: killing this helper releases its dynamic policies; other apps untouched.
        new Thread(() -> {
            try { Thread.sleep(20000); } catch (InterruptedException ignored) { }
            if (!finished.get()) System.exit(0);
        }, "audio-test-hard-deadline").start();
        try {
            guard();
            if (cancel.get()) throw new IOException("Test annulé");
            stage = "create_voip_routes";
            AudioFormat in = new AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            AudioFormat out = new AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            AudioRecord.Builder rb = new AudioRecord.Builder().setAudioFormat(in);
            AudioRecord.Builder.class.getMethod("setContext", Context.class).invoke(rb, context);
            AudioRecord.Builder.class.getMethod("setCallRedirectionMode", int.class).invoke(rb, 2);
            record = rb.build();
            AudioTrack.Builder tb = new AudioTrack.Builder().setAudioFormat(out).setTransferMode(AudioTrack.MODE_STREAM);
            AudioTrack.Builder.class.getMethod("setContext", Context.class).invoke(tb, context);
            AudioTrack.Builder.class.getMethod("setCallRedirectionMode", int.class).invoke(tb, 2);
            track = tb.build();
            guard();
            if (cancel.get()) throw new IOException("Test annulé");
            stage = "start_voip_routes";
            record.startRecording(); captureStarted = true;
            track.play(); playbackStarted = true;
            stage = "signal_test";
            report = "{\"state\":\"running\",\"duration_seconds\":8,\"whatsapp_bidirectional_verified\":false}";
            long start = SystemClock.elapsedRealtime(), checked = start;
            int frame = 0;
            while (!cancel.get() && SystemClock.elapsedRealtime() - start < 8000) {
                if (manager.getMode() != AudioManager.MODE_IN_COMMUNICATION) throw new IOException("Appel terminé ou mode audio changé");
                long now = SystemClock.elapsedRealtime();
                if (now - checked >= 700) { guard(); checked = SystemClock.elapsedRealtime(); }
                int count = record.read(incoming, 0, incoming.length, AudioRecord.READ_NON_BLOCKING);
                if (count < 0) throw new IOException("Capture audio code " + count);
                for (int i = 0; i < count; i++) { int value = incoming[i]; peak = Math.max(peak, Math.abs(value)); energy += (double)value * value; }
                samples += count;
                // Test tones at 1, 3 and 5 seconds; short fades, modest amplitude.
                long elapsed = SystemClock.elapsedRealtime() - start;
                int beep = elapsed >= 1000 && elapsed < 1250 ? 440 : elapsed >= 3000 && elapsed < 3250 ? 660 : elapsed >= 5000 && elapsed < 5250 ? 880 : 0;
                for (int i = 0; i < outgoing.length; i++) outgoing[i] = beep == 0 ? 0 : (short)(1800 * Math.sin(2 * Math.PI * beep * (frame * 480L + i) / 24000) * Math.sin(Math.PI * i / outgoing.length));
                int accepted = track.write(outgoing, 0, outgoing.length, AudioTrack.WRITE_NON_BLOCKING);
                if (accepted < 0) throw new IOException("Injection audio code " + accepted);
                written += accepted; frame++;
                Thread.sleep(20);
            }
            outcome = cancel.get() ? "cancelled" : "completed";
        } catch (Throwable e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            error = cause.getClass().getSimpleName() + ": " + cause.getMessage();
        } finally {
            if (record != null) { try { if (captureStarted) record.stop(); } catch (Exception ignored) {} try { record.release(); } catch (Exception ignored) {} }
            if (track != null) { try { if (playbackStarted) track.pause(); track.flush(); } catch (Exception ignored) {} try { track.release(); } catch (Exception ignored) {} }
            java.util.Arrays.fill(incoming, (short)0); java.util.Arrays.fill(outgoing, (short)0);
            try {
                report = new JSONObject().put("state", outcome).put("stage", stage).put("error", error)
                    .put("owner_diagnostic", lastOwnerDiagnostic)
                    .put("capture_started", captureStarted).put("playback_started", playbackStarted)
                    .put("captured_samples", samples).put("written_samples", written).put("peak", peak)
                    .put("rms", samples == 0 ? 0 : Math.sqrt(energy/samples))
                    .put("audio_saved", false).put("audio_sent_to_ai", false)
                    .put("whatsapp_bidirectional_verified", false)
                    .put("human_check", "Le correspondant a-t-il entendu trois bips ? Lui demander après le test.")
                    .toString();
            } catch (Exception ignored) { report = "{\"state\":\"report_failed\"}"; }
            finished.set(true); busy.set(false);
        }
    }
}
