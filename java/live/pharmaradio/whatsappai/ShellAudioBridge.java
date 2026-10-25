package live.pharmaradio.whatsappai;

import android.content.Context;
import android.content.AttributionSource;
import android.media.AudioManager;
import android.media.AudioFormat;
import android.telecom.TelecomManager;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Private transport plus explicit bounded VoIP test. Never dials or changes audio mode. */
public final class ShellAudioBridge {
    private static final String PACKAGE = "live.pharmaradio.whatsappai";
    private static final String PROCESS = "whatsapp-ai-bridge";
    public static void main(String[] args) throws Exception {
        if (android.os.Process.myUid() != 2000) throw new SecurityException("Shell requis");
        Looper.prepareMainLooper();
        Class<?> type = Class.forName("android.app.ActivityThread");
        Object thread = type.getMethod("systemMain").invoke(null);
        Context baseContext = ((Context) type.getMethod("getSystemContext").invoke(thread)).createPackageContext("com.android.shell", 0);
        java.lang.reflect.Field opPackage = baseContext.getClass().getDeclaredField("mOpPackageName");
        opPackage.setAccessible(true); opPackage.set(baseContext, "com.android.shell");
        java.lang.reflect.Field attribution = baseContext.getClass().getDeclaredField("mAttributionSource");
        attribution.setAccessible(true); attribution.set(baseContext, new AttributionSource.Builder(2000).setPackageName("com.android.shell").build());
        // AudioPolicy constructs its sink without the builder Context. Align the
        // helper process's default attribution with its actual shell UID too.
        android.app.Application app = (android.app.Application) type.getMethod("currentApplication").invoke(null);
        if (app == null) throw new SecurityException("Attribution globale absente");
        Context appBase = app.getBaseContext();
        opPackage.set(appBase, "com.android.shell");
        attribution.set(appBase, new AttributionSource.Builder(android.os.Process.myUid())
                .setPackageName("com.android.shell").build());
        AttributionSource global = (AttributionSource) type.getMethod("currentAttributionSource").invoke(null);
        if (global == null || global.getUid() != 2000 || !"com.android.shell".equals(global.getPackageName()))
            throw new SecurityException("Attribution shell incohérente");
        Context context = new android.content.ContextWrapper(baseContext) {
            @Override public Context getApplicationContext() { return this; }
        };
        if (args.length == 1 && "--activate-only".equals(args[0])) {
            activate(context);
            System.out.println("VOICE_LOCAL_ACTIVATED");
            System.exit(0);
        }
        if (args.length == 1 && "--diagnose-owner".equals(args[0])) {
            AudioManager manager=context.getSystemService(AudioManager.class);
            System.out.println(new VoipAudioTest(context,manager).inspectOwner().toString());
            System.exit(0);
        }
        if(args.length==1 && ("--local-audio-selftest-3".equals(args[0]) || "--local-audio-selftest-6".equals(args[0])
                || "--local-audio-selftest-bytes-3".equals(args[0]) || "--local-audio-selftest-bytes-6".equals(args[0]))) {
            AudioManager benchAudio=AudioManager.class.getConstructor(Context.class).newInstance(context);
            System.out.println(LocalAudioBench.run(context,benchAudio,args[0].endsWith("6")?6:3,args[0].contains("-bytes-")));
            System.exit(0);
        }
        if(args.length==1 && args[0].matches("--socket-uid-bench-(3|6)-(dry|effects)")) {
            AudioManager benchAudio=AudioManager.class.getConstructor(Context.class).newInstance(context);
            System.out.println(CrossUidBench.run(context,benchAudio,args[0].contains("-6-")?6:3,args[0].endsWith("effects"),true));System.exit(0);
        }
        if(args.length==1 && args[0].matches("--cross-uid-bench-(3|6)-(dry|effects)")) {
            AudioManager benchAudio=AudioManager.class.getConstructor(Context.class).newInstance(context);
            System.out.println(CrossUidBench.run(context,benchAudio,args[0].contains("-6-")?6:3,args[0].endsWith("effects")));
            System.exit(0);
        }
        if (args.length != 0) throw new IllegalArgumentException("Option non autorisée");
        final int appUid = context.getPackageManager().getApplicationInfo(PACKAGE, 0).uid;
        AudioManager audio = context.getSystemService(AudioManager.class);
        VoipAudioTest audioTest = new VoipAudioTest(context, audio);
        VoipPcmSession pcm = new VoipPcmSession(context,audio);
        Object sessions = new Object();
        final long[] launchExpiry={0}; final LaunchAttempt[] launchAttempt={null}; final String[] launchNonce={null};
        final java.util.concurrent.atomic.AtomicReference<String> launchReport=new java.util.concurrent.atomic.AtomicReference<>("{\"phase\":\"idle\"}");
        final String[] preparedCall=new String[2];final long[] preparedUntil=new long[1];
        final String[] activeHangup=new String[2];
        java.util.concurrent.atomic.AtomicBoolean benchRunning=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<String> benchReport=new java.util.concurrent.atomic.AtomicReference<>("{\"state\":\"idle\"}");
        final long expires = SystemClock.elapsedRealtime() + 30 * 60 * 1000;
        Binder endpoint = new Binder() {
            @Override protected boolean onTransact(int code, Parcel request, Parcel reply, int flags) {
                if (Binder.getCallingUid() != appUid) throw new SecurityException("Application non autorisée");
                request.enforceInterface(BridgeProvider.DESCRIPTOR);
                long identity = Binder.clearCallingIdentity();
                try {
                    if (code == 1) {
                        JSONObject status = new JSONObject().put("version", "0.22-pcm-transport")
                            .put("target_package", "com.whatsapp").put("helper_uid", android.os.Process.myUid())
                            .put("mode", audio.getMode()).put("audio_test_running", audioTest.running()||pcm.running()||benchRunning.get())
                            .put("whatsapp_bidirectional_verified", false)
                            .put("expires_in_seconds", pcm.running() ? -1 : Math.max(0, (expires - SystemClock.elapsedRealtime()) / 1000))
                            .put("active_call_time_limit_seconds", 0);
                        for (String permission : new String[]{"CALL_AUDIO_INTERCEPTION", "CAPTURE_AUDIO_OUTPUT", "MODIFY_AUDIO_ROUTING", "ANSWER_PHONE_CALLS"})
                            status.put(permission, context.checkSelfPermission("android.permission." + permission) == 0);
                        status.put("downlink_api", AudioManager.class.getMethod("getCallDownlinkExtractionAudioRecord", AudioFormat.class) != null);
                        status.put("uplink_api", AudioManager.class.getMethod("getCallUplinkInjectionAudioTrack", AudioFormat.class) != null);
                        reply.writeNoException(); reply.writeString(status.toString());
                    } else if (code == 3) {
                        // One nonce, never audio; finite worker and descriptor lifetime.
                        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createSocketPair();
                        Thread worker = new Thread(() -> {
                            try (ParcelFileDescriptor fd = pair[1]) {
                                DataInputStream input = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(fd));
                                DataOutputStream output = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(fd));
                                output.writeLong(input.readLong()); output.flush();
                            } catch (IOException ignored) { }
                        }, "relay-channel-check");
                        worker.start();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try { android.system.Os.shutdown(pair[1].getFileDescriptor(), android.system.OsConstants.SHUT_RDWR); } catch (Exception ignored) { }
                            try { pair[1].close(); } catch (Exception ignored) { }
                        }, 4000);
                        reply.writeNoException(); reply.writeTypedObject(pair[0], Parcelable.PARCELABLE_WRITE_RETURN_VALUE); pair[0].close();
                    } else if (code == 4) {
                        reply.writeNoException();
                        new Handler(Looper.getMainLooper()).postDelayed(() -> System.exit(0), 100);
                    } else if (code == 5) {
                        if (!"whatsapp-eight-second-test-consented".equals(request.readString())) throw new SecurityException("Confirmation requise");
                        synchronized(sessions) {
                            if(pcm.running()||benchRunning.get()) throw new IllegalStateException("Transport PCM actif");
                            audioTest.start();
                        }
                        reply.writeNoException();
                    } else if (code == 6) {
                        reply.writeNoException(); reply.writeString(audioTest.status());
                    } else if (code == 7) {
                        audioTest.stop(); reply.writeNoException();
                    } else if (code == 8) {
                        if(pcm.running()) throw new IllegalStateException("Diagnostic déjà assuré par le transport");
                        String diagnostic = audioTest.inspectOwner().toString();
                        reply.writeNoException(); reply.writeString(diagnostic);
                    } else if (code == 9 || code == 14) {
                        String consent=code==14?"whatsapp-ai-session-consented":"whatsapp-pcm-thirty-seconds-consented";
                        if(!consent.equals(request.readString())) throw new SecurityException("Confirmation PCM requise");
                        synchronized(sessions) {
                            if(audioTest.running()||benchRunning.get()) throw new IllegalStateException("Test audio actif");
                            String expected=null;
                            String token=null;
                            if(code==14){
                                token=request.readString();
                                if(token==null||!token.equals(preparedCall[0])||SystemClock.elapsedRealtime()>preparedUntil[0])throw new SecurityException("Preparation expiree");
                                expected=preparedCall[1];preparedCall[0]=null;preparedCall[1]=null;
                            }
                            ParcelFileDescriptor fd=pcm.open(code==14,expected);
                            if(code==14){activeHangup[0]=token;activeHangup[1]=expected;}
                            try { reply.writeNoException(); reply.writeTypedObject(fd,Parcelable.PARCELABLE_WRITE_RETURN_VALUE); }
                            finally { fd.close(); }
                        }
                    } else if (code == 10) {
                        reply.writeNoException(); reply.writeString(pcm.status());
                    } else if (code == 11) {
                        synchronized(sessions){activeHangup[0]=null;activeHangup[1]=null;pcm.stop();}
                        reply.writeNoException();
                    } else if(code==20){
                        String token=request.readString();
                        synchronized(sessions){
                            if(token==null||!token.equals(activeHangup[0])||activeHangup[1]==null||!pcm.running())
                                throw new SecurityException("Aucune mission audio autorisee");
                            JSONObject owner=audioTest.inspectOwner();
                            if(!owner.optBoolean("strict_policy_allows")||!activeHangup[1].equals(audioTest.sessionKey()))
                                throw new SecurityException("Appel WhatsApp different ou concurrent");
                            if(context.checkSelfPermission("android.permission.ANSWER_PHONE_CALLS")!=0)
                                throw new SecurityException("Raccrochage indisponible");
                            activeHangup[0]=null;activeHangup[1]=null;
                            TelecomManager telecom=context.getSystemService(TelecomManager.class);
                            if(telecom==null)throw new IllegalStateException("Telecom indisponible");
                            boolean requested=telecom.endCall();
                            boolean gone=false;
                            if(requested)try{
                                Thread.sleep(700);
                                gone=audioTest.inspectOwner().optBoolean("no_current_calls_verified");
                            }catch(Exception unavailable){gone=false;}
                            reply.writeNoException();reply.writeString(new JSONObject().put("hangup_request_accepted",requested)
                                .put("no_current_call_observed",gone).put("target","com.whatsapp").toString());
                        }
                    } else if(code==12){
                        if(!"local-synthetic-no-call".equals(request.readString()))throw new SecurityException("Local bench confirmation missing");
                        synchronized(sessions){
                            if(audioTest.running()||pcm.running()||!benchRunning.compareAndSet(false,true))throw new IllegalStateException("Audio busy");
                            benchReport.set("{\"state\":\"starting\"}");
                            new Thread(()->{
                                try{AudioManager own=AudioManager.class.getConstructor(Context.class).newInstance(context);
                                    benchReport.set(CrossUidBench.run(context,own,3,true,true).toString());
                                }catch(Exception e){benchReport.set("{\"state\":\"failed\",\"error\":\"local_bench_start\"}");}
                                finally{benchRunning.set(false);}
                            },"local-ui-bench").start();
                        }
                        reply.writeNoException();
                    } else if(code==13){
                        reply.writeNoException();reply.writeString(benchReport.get());
                    } else if(code==16){
                        synchronized(sessions){
                            if(audioTest.running()||pcm.running()||benchRunning.get())throw new IllegalStateException("Audio actif");
                            if(launchAttempt[0]!=null&&SystemClock.elapsedRealtime()>=launchExpiry[0]){launchAttempt[0].cancel();launchAttempt[0]=null;launchNonce[0]=null;}
                            if(launchAttempt[0]!=null)throw new IllegalStateException("Preparation active");
                            LaunchAttempt a=new LaunchAttempt();
                            JSONObject idle=audioTest.inspectOwner();
                            if(!a.arm(idle.optBoolean("no_current_calls_verified")&&idle.optInt("audio_manager_mode",-1)==0,SystemClock.elapsedRealtime()))throw new SecurityException("Appel deja present");
                            launchExpiry[0]=SystemClock.elapsedRealtime()+120000;launchAttempt[0]=a;launchNonce[0]=java.util.UUID.randomUUID().toString();
                            launchReport.set("{\"phase\":\"armed\"}");
                            reply.writeNoException();reply.writeString(launchNonce[0]);
                        }
                    } else if(code==17||code==18){
                        synchronized(sessions){
                            String nonce=request.readString();
                            if(nonce==null||!nonce.equals(launchNonce[0])||launchAttempt[0]==null)throw new SecurityException("Preparation inconnue");
                            if(code==18){launchAttempt[0].cancel();launchAttempt[0]=null;launchNonce[0]=null;reply.writeNoException();reply.writeString("cancelled");}
                            else {
                                boolean ready=audioTest.observeLaunch(launchAttempt[0]);
                                LaunchAttempt attempt=launchAttempt[0];
                                launchReport.set(new JSONObject().put("phase",attempt.failed()?"rejected":ready?"active_ready":attempt.sawDialing()?"dialing_seen":"waiting")
                                        .put("reason",attempt.reason()).put("last_state",attempt.lastState()).put("observations",attempt.observations()).toString());
                                String answer="waiting";
                                if(launchAttempt[0].failed()){answer="failed";launchAttempt[0]=null;launchNonce[0]=null;}
                                else if(ready){
                                    String expected=launchAttempt[0].consume();
                                    if(!audioTest.inspectOwner().optBoolean("strict_policy_allows")||!expected.equals(audioTest.sessionKey())){
                                        launchReport.set("{\"phase\":\"owner_rejected\"}");throw new SecurityException("Appel change");
                                    }
                                    preparedCall[0]=java.util.UUID.randomUUID().toString();preparedCall[1]=expected;preparedUntil[0]=SystemClock.elapsedRealtime()+90000;
                                    answer=preparedCall[0];launchAttempt[0]=null;launchNonce[0]=null;
                                    launchReport.set("{\"phase\":\"prepared\"}");
                                }
                                reply.writeNoException();reply.writeString(answer);
                            }
                        }
                    } else if(code==19){
                        reply.writeNoException();reply.writeString(launchReport.get());
                    } else if(code==15){
                        synchronized(sessions){
                            if(audioTest.running()||pcm.running()||benchRunning.get())throw new IllegalStateException("Audio actif");
                            if(!audioTest.inspectOwner().optBoolean("strict_policy_allows"))throw new SecurityException("Appel non confirme");
                            preparedCall[0]=java.util.UUID.randomUUID().toString();preparedCall[1]=audioTest.sessionKey();preparedUntil[0]=SystemClock.elapsedRealtime()+90000;
                            reply.writeNoException();reply.writeString(preparedCall[0]);
                        }
                    } else throw new SecurityException("Opération non disponible");
                } catch (Exception error) {
                    reply.writeException(new IllegalStateException(error.getClass().getSimpleName()));
                } finally { Binder.restoreCallingIdentity(identity); }
                return true;
            }
        };
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable publisher = new Runnable() {
            @Override public void run() {
                long now=SystemClock.elapsedRealtime();
                synchronized(sessions){
                    if(now>=expires && !pcm.running() && !audioTest.running() && !benchRunning.get()
                            && (launchAttempt[0]==null || now>=launchExpiry[0])
                            && (preparedCall[0]==null || now>=preparedUntil[0]))System.exit(0);
                }
                Object manager = null; IBinder token = new Binder();
                try {
                    Class<?> managerType = Class.forName("android.app.IActivityManager");
                    manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
                    Object holder = managerType.getMethod("getContentProviderExternal", String.class, int.class, IBinder.class, String.class)
                        .invoke(manager, BridgeProvider.AUTHORITY, 0, token, "whatsapp-ai");
                    if (holder == null) throw new IllegalStateException("Provider absent");
                    Bundle extras = new Bundle(); extras.putBinder("bridge", endpoint);
                    Class.forName("android.content.IContentProvider").getMethod("call", AttributionSource.class, String.class, String.class, String.class, Bundle.class)
                        .invoke(holder.getClass().getField("provider").get(holder), new AttributionSource.Builder(2000).setPackageName("com.android.shell").build(), BridgeProvider.AUTHORITY, "publish", null, extras);
                } catch (Exception error) { System.err.println("Publication: " + error.getClass().getSimpleName()); }
                finally {
                    if (manager != null) try { Class.forName("android.app.IActivityManager").getMethod("removeContentProviderExternalAsUser", String.class, IBinder.class, int.class).invoke(manager, BridgeProvider.AUTHORITY, token, 0); } catch (Exception ignored) { }
                }
                handler.postDelayed(this, 5000);
            }
        };
        handler.post(publisher);
        Looper.loop();
    }

    private static void activate(Context context) throws Exception {
        AudioManager audio = context.getSystemService(AudioManager.class);
        if (audio.getMode() != AudioManager.MODE_NORMAL) throw new IllegalStateException("Terminez la communication avant activation");
        java.lang.Process existing = new ProcessBuilder("/system/bin/pidof", PROCESS).start();
        if (!existing.waitFor(3, TimeUnit.SECONDS)) { existing.destroy(); throw new IOException("Délai dépassé"); }
        if (existing.exitValue() == 0) return;
        File apk = new File(context.getPackageManager().getApplicationInfo(PACKAGE, 0).sourceDir).getCanonicalFile();
        LocalActivationPolicy.activationCommand(apk.getPath());
        ProcessBuilder process = new ProcessBuilder("/system/bin/nohup", "/system/bin/app_process", "/", "--nice-name=" + PROCESS, PACKAGE + ".ShellAudioBridge");
        process.environment().put("CLASSPATH", apk.getPath());
        process.redirectInput(new File("/dev/null"));
        process.redirectOutput(ProcessBuilder.Redirect.appendTo(new File("/data/local/tmp/whatsapp-ai-bridge.log")));
        process.redirectErrorStream(true);
        process.start();
    }
}
