package live.pharmaradio.whatsappai;
import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;
import org.json.JSONArray;

/** SIM bridge architecture adapted to VoIP: no Android mode changes, no stored PCM. */
final class VoipPcmSession {
    private final Context context;
    private final AudioManager audio;
    private final AtomicBoolean live=new AtomicBoolean();
    private volatile Run current;
    private volatile String report="{\"state\":\"idle\"}";
    VoipPcmSession(Context c,AudioManager a) { context=c; audio=a; }
    boolean running() { return live.get(); }
    String status() { return report; }
    void stop() { Run r=current; if(r!=null) r.stop(); }
    synchronized ParcelFileDescriptor open() throws Exception {return open(false,null);}
    synchronized ParcelFileDescriptor open(boolean continuous,String expectedCall) throws Exception {
        if(!live.compareAndSet(false,true)) throw new IOException("Transport déjà actif");
        ParcelFileDescriptor[] pair=null;
        try {
            pair=ParcelFileDescriptor.createSocketPair();
            PcmSocket.nonblocking(pair[0]); PcmSocket.nonblocking(pair[1]);
            Run r=new Run(new PcmSocket(pair[1]),continuous,expectedCall); current=r;
            report="{\"state\":\"starting\"}";
            new Thread(r,"whatsapp-pcm-session").start();
            return pair[0];
        } catch(Exception e) {
            if(pair!=null) for(ParcelFileDescriptor fd:pair) try { fd.close(); } catch(Exception ignored) {}
            live.set(false); throw e;
        }
    }
    private final class Run implements Runnable {
        final PcmSocket wire;
        final int maxDurationMs;
        final String expectedCall;
        final GuardLease lease=new GuardLease();
        final VoipAudioTest owner=new VoipAudioTest(context,audio);
        final AtomicBoolean stopped=new AtomicBoolean(), done=new AtomicBoolean();
        final AtomicLong captured=new AtomicLong(),written=new AtomicLong();
        final AtomicLong received=new AtomicLong(),frames=new AtomicLong();
        final AtomicLong captureProgress=new AtomicLong(SystemClock.elapsedRealtime()),playbackProgress=new AtomicLong(SystemClock.elapsedRealtime());
        volatile int trackRate,trackEncoding,trackChannels,writeRequested,writeReturned;
        long uplinkEnergy; int uplinkPeak;
        final JSONArray routeSamples=new JSONArray();
        final AtomicReference<String> error=new AtomicReference<>("");
        final AtomicReference<String> guardLoss=new AtomicReference<>("");
        volatile String stage="verify_whatsapp_owner";
        Run(PcmSocket w,boolean continuous,String expectedCall) { wire=w;maxDurationMs=continuous?0:30000;this.expectedCall=expectedCall; }
        void stop() { stopped.set(true); lease.close(); wire.close(); }
        void fail(String message) { error.compareAndSet("",message); stop(); }
        boolean valid() {
            if(stopped.get())return false;
            if(audio.getMode()!=AudioManager.MODE_IN_COMMUNICATION){guardLoss.compareAndSet("","audio_mode_changed");return false;}
            if(!lease.valid(SystemClock.elapsedRealtime())){guardLoss.compareAndSet("","identity_lease_expired");return false;}
            return true;
        }
        void verify() throws Exception {
            long begin=SystemClock.elapsedRealtime();
            JSONObject d=owner.inspectOwner();
            if(expectedCall!=null&&!expectedCall.equals(owner.sessionKey())){
                guardLoss.compareAndSet("","call_identity_changed");throw new IOException("Appel different de la preparation consentie");
            }
            if(!d.optBoolean("strict_policy_allows")){
                guardLoss.compareAndSet("","whatsapp_identity_unconfirmed");throw new IOException("Appel changé, absent ou identité trop ancienne");
            }
            if(SystemClock.elapsedRealtime()-begin>=1500){
                guardLoss.compareAndSet("","owner_check_too_slow");throw new IOException("Appel changé, absent ou identité trop ancienne");
            }
            if(!lease.renew(owner.sessionKey(),begin,SystemClock.elapsedRealtime())){
                guardLoss.compareAndSet("","identity_lease_expired");throw new IOException("Appel changé, absent ou identité trop ancienne");
            }
        }
        @Override public void run() {
            AudioRecord record=null; AudioTrack track=null;
            Thread capture=null, playback=null, monitor=null;
            boolean started=false, completed=false;
            long startedAt=0;
            // A stalled platform route must not retain interception indefinitely.
            new Thread(() -> {
                try { while(!done.get()) {
                    Thread.sleep(5000);
                    long now=SystemClock.elapsedRealtime();
                    if(!done.get() && (now-captureProgress.get()>20000 || now-playbackProgress.get()>20000))System.exit(0);
                } } catch(InterruptedException ignored) {}
            },"pcm-stall-watchdog").start();
            try {
                verify(); if(stopped.get()) throw new IOException("Test annulé");
                stage="create_voip_routes";
                AudioFormat in=new AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
                AudioFormat out=new AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                AudioRecord.Builder rb=new AudioRecord.Builder().setAudioFormat(in);
                AudioRecord.Builder.class.getMethod("setContext",Context.class).invoke(rb,context);
                AudioRecord.Builder.class.getMethod("setCallRedirectionMode",int.class).invoke(rb,2);
                record=rb.build();
                AudioTrack.Builder tb=new AudioTrack.Builder().setAudioFormat(out).setTransferMode(AudioTrack.MODE_STREAM);
                AudioTrack.Builder.class.getMethod("setContext",Context.class).invoke(tb,context);
                AudioTrack.Builder.class.getMethod("setCallRedirectionMode",int.class).invoke(tb,2);
                track=tb.build(); verify();
                trackRate=track.getSampleRate(); trackEncoding=track.getAudioFormat(); trackChannels=track.getChannelCount();
                if(trackRate!=24000 || trackEncoding!=AudioFormat.ENCODING_PCM_16BIT || trackChannels!=1)
                    throw new IOException("Format injection différent du PCM négocié");
                if(!valid()) throw new IOException("Appel expiré avant démarrage");
                record.startRecording(); track.play(); started=true;
                captureProgress.set(SystemClock.elapsedRealtime());playbackProgress.set(SystemClock.elapsedRealtime());
                AudioRecord activeRecord=record; AudioTrack activeTrack=track;
                stage="pcm_stream"; startedAt=SystemClock.elapsedRealtime();
                report="{\"state\":\"running\",\"duration_seconds_max\":"+(maxDurationMs/1000)+"}";
                capture=new Thread(() -> {
                    byte[] buffer=new byte[PcmProtocol.FRAME];
                    long last=SystemClock.elapsedRealtime();
                    try {
                        while(valid()) {
                            int n=activeRecord.read(buffer,0,buffer.length,AudioRecord.READ_NON_BLOCKING);
                            if(n<0 || n>buffer.length || (n&1)!=0) throw new IOException("Capture PCM interrompue");
                            if(n>0) { wire.write(buffer,n,500); captured.addAndGet(n); last=SystemClock.elapsedRealtime();captureProgress.set(last); }
                            else { if(SystemClock.elapsedRealtime()-last>1000) throw new IOException("Capture PCM silencieuse côté transport"); Thread.sleep(3); }
                        }
                        if(!stopped.get()) fail("Capture arrêtée par le contrôle d'appel");
                    } catch(Exception e) { if(!stopped.get()) fail("capture: "+e.getClass().getSimpleName()); }
                    finally { Arrays.fill(buffer,(byte)0); }
                },"whatsapp-downlink-pcm");
                playback=new Thread(() -> {
                    byte[] buffer=null;
                    try {
                        while(valid()) {
                            buffer=wire.read(1000);
                            received.addAndGet(buffer.length); if(buffer.length>0) frames.incrementAndGet();
                            for(int i=0;i<buffer.length;i+=2) { int v=(short)((buffer[i]&255)|(buffer[i+1]<<8)); uplinkPeak=Math.max(uplinkPeak,Math.abs(v)); uplinkEnergy+=(long)v*v; }
                            int offset=0; long until=SystemClock.elapsedRealtime()+200;
                            while(offset<buffer.length && valid()) {
                                int n=activeTrack.write(buffer,offset,buffer.length-offset,AudioTrack.WRITE_NON_BLOCKING);
                                if(n<0 || n>buffer.length-offset || (n&1)!=0) {
                                    writeRequested=buffer.length-offset; writeReturned=n;
                                    throw new IOException("Retour injection PCM invalide");
                                }
                                if(n==0) { if(SystemClock.elapsedRealtime()>=until) throw new IOException("Injection PCM en retard"); Thread.sleep(3); }
                                else { offset+=n; written.addAndGet(n); }
                            }
                            playbackProgress.set(SystemClock.elapsedRealtime());Arrays.fill(buffer,(byte)0); buffer=null;
                        }
                        if(!stopped.get()) fail("Injection arrêtée par le contrôle d'appel");
                    } catch(Exception e) { if(!stopped.get()) fail("injection: "+e.getClass().getSimpleName()); }
                    finally { if(buffer!=null) Arrays.fill(buffer,(byte)0); }
                },"whatsapp-uplink-pcm");
                monitor=new Thread(() -> {
                    try { while(!stopped.get()) { Thread.sleep(300); if(!stopped.get()) verify(); } }
                    catch(Exception e) { if(!stopped.get()) fail("identity: "+e.getClass().getSimpleName()); }
                },"whatsapp-call-identity");
                capture.start(); playback.start(); monitor.start();
                // Diagnostics may block long enough to starve the injector at startup.
                // Start the PCM owners first; no second writer and no guard relaxation.
                routeSamples.put(RouteDiagnostics.snapshot(context,audio,track,record)
                    .put("elapsed_ms",SystemClock.elapsedRealtime()-startedAt));
                long nextRouteSample=startedAt+1000;
                while(!stopped.get()) {
                    if(!valid()) { fail("Appel terminé ou contrôle d'identité expiré"); break; }
                    if(maxDurationMs>0&&SystemClock.elapsedRealtime()-startedAt>=maxDurationMs) { completed=true; break; }
                    if(SystemClock.elapsedRealtime()>=nextRouteSample && routeSamples.length()<16) {
                        routeSamples.put(RouteDiagnostics.snapshot(context,audio,track,record)
                            .put("elapsed_ms",SystemClock.elapsedRealtime()-startedAt));
                        nextRouteSample=SystemClock.elapsedRealtime()+2000;
                    }
                    Thread.sleep(20);
                }
            } catch(Exception e) { if(!stopped.get()) error.compareAndSet("",e.getClass().getSimpleName()+": "+e.getMessage()); }
            finally {
                stop();
                if(record!=null) try { record.stop(); } catch(Exception ignored) {}
                if(track!=null) try { track.pause(); track.flush(); } catch(Exception ignored) {}
                for(Thread worker:new Thread[]{capture,playback,monitor}) if(worker!=null) {
                    try { worker.join(1500); } catch(InterruptedException ignored) {}
                    if(worker.isAlive()) System.exit(0);
                }
                if(record!=null) record.release(); if(track!=null) track.release();
                try { report=new JSONObject().put("state",!error.get().isEmpty()?"failed":completed?"completed":"stopped")
                    .put("stage",stage).put("error",error.get()).put("guard_loss",guardLoss.get()).put("routes_started",started)
                    .put("captured_bytes",captured.get()).put("written_bytes",written.get())
                    .put("uplink_received_bytes",received.get()).put("uplink_received_frames",frames.get())
                    .put("route_samples",routeSamples)
                    .put("track_rate",trackRate).put("track_encoding",trackEncoding).put("track_channels",trackChannels)
                    .put("invalid_write_requested",writeRequested).put("invalid_write_returned",writeReturned)
                    .put("uplink_peak",uplinkPeak).put("uplink_rms",received.get()==0?0:Math.sqrt(uplinkEnergy/(received.get()/2.0)))
                    .put("elapsed_ms",startedAt==0?0:SystemClock.elapsedRealtime()-startedAt)
                    .put("duration_seconds_max",maxDurationMs/1000)
                    .put("pcm_transport",true).put("audio_saved",false).put("audio_sent_to_ai",false)
                    .put("human_audio_verified",false).toString(); } catch(Exception ignored) {}
                done.set(true); live.set(false);
            }
        }
    }
}
