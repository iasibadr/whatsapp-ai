package live.pharmaradio.whatsappai;

import android.content.Context;
import android.media.*;
import android.os.SystemClock;
import java.io.*;
import java.nio.channels.*;
import java.util.Arrays;
import java.util.concurrent.atomic.*;
import org.json.*;

/** Synthetic endpoints only. Separate from the real-call guard; no network or dialing. */
final class LocalAudioBench {
    static JSONObject run(Context context, AudioManager audio, int mode,boolean byteWrites) {
        JSONObject result=new JSONObject();
        AudioRecord extracted=null, simulatedMic=null;
        AudioTrack injected=null, simulatedSpeaker=null;
        AtomicBoolean done=new AtomicBoolean(),stop=new AtomicBoolean();
        AtomicLong checkedAt=new AtomicLong();
        Thread monitor=null;
        boolean ownsMode=false;
        ToneEvidence up=new ToneEvidence(16000,1000),down=new ToneEvidence(24000,1500);
        ToneEvidence quietUp=new ToneEvidence(16000,1000),quietDown=new ToneEvidence(24000,1500);
        long upWritten=0,downWritten=0,upRead=0,downRead=0;
        String stage="preconditions";
        short[] upSignal=new short[480],downSignal=new short[480],upInput=new short[3200],downInput=new short[4800];
        byte[] upBytes=new byte[960];
        VoipAudioTest inspector=new VoipAudioTest(context,audio);
        AtomicReference<String> guardFailure=new AtomicReference<>("");
        try(FileOutputStream lockFile=new FileOutputStream("/data/local/tmp/whatsapp-relay-local-bench.lock",true);
            FileChannel channel=lockFile.getChannel(); FileLock lock=channel.tryLock()) {
            if(lock==null) throw new IOException("Banc local déjà actif");
            if(mode!=3 && mode!=6) throw new IOException("Mode de banc inconnu");
            JSONObject before=inspector.inspectOwner();
            if(!before.optBoolean("no_current_calls_verified") || audio.getMode()!=0
                    || !before.optBoolean("owner_none") || !audio.getActiveRecordingConfigurations().isEmpty())
                throw new IOException("Banc refusé : appel, mode ou enregistrement actif");
            new Thread(() -> { SystemClock.sleep(18000); if(!done.get()) System.exit(2); },"bench-deadline").start();
            audio.setMode(mode); ownsMode=true;
            guard(inspector,audio,mode); checkedAt.set(SystemClock.elapsedRealtime());
            stage="create_synthetic_routes";
            AudioFormat in24=format(24000,true),out24=format(24000,false);
            AudioRecord.Builder er=new AudioRecord.Builder().setAudioFormat(in24);
            AudioRecord.Builder.class.getMethod("setContext",Context.class).invoke(er,context);
            AudioRecord.Builder.class.getMethod("setCallRedirectionMode",int.class).invoke(er,2);
            extracted=er.build();
            AudioTrack.Builder it=new AudioTrack.Builder().setAudioFormat(out24).setTransferMode(AudioTrack.MODE_STREAM);
            AudioTrack.Builder.class.getMethod("setContext",Context.class).invoke(it,context);
            AudioTrack.Builder.class.getMethod("setCallRedirectionMode",int.class).invoke(it,2);
            injected=it.build();
            AudioRecord.Builder mr=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(format(16000,true)).setBufferSizeInBytes(16000);
            AudioRecord.Builder.class.getMethod("setContext",Context.class).invoke(mr,context);
            simulatedMic=mr.build();
            AudioTrack.Builder sp=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(out24).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(9600);
            AudioTrack.Builder.class.getMethod("setContext",Context.class).invoke(sp,context);
            simulatedSpeaker=sp.build();
            guard(inspector,audio,mode); checkedAt.set(SystemClock.elapsedRealtime());
            extracted.startRecording(); injected.play(); simulatedMic.startRecording(); simulatedSpeaker.play();
            monitor=new Thread(() -> {
                try { while(!stop.get()) { guard(inspector,audio,mode); checkedAt.set(SystemClock.elapsedRealtime()); SystemClock.sleep(250); } }
                catch(Exception e) { guardFailure.set(e.getClass().getSimpleName()); stop.set(true); }
            },"bench-no-call-monitor");
            monitor.start();
            stage="verify_virtual_routes";
            long routeDeadline=SystemClock.elapsedRealtime()+1200;
            while(!same(injected.getRoutedDevice(),simulatedMic.getRoutedDevice())
                    || !same(simulatedSpeaker.getRoutedDevice(),extracted.getRoutedDevice())) {
                if(stop.get() || SystemClock.elapsedRealtime()>=routeDeadline)
                    throw new IOException("Routes virtuelles non confirmées : aucun signal émis ni lu");
                SystemClock.sleep(10);
            }
            stage="synthetic_pcm";
            int routeHoldMs=Integer.getInteger("jarvis.bench.routeHoldMs",0);
            if(routeHoldMs<0 || routeHoldMs>1000)throw new IOException("Invalid diagnostic hold");
            result.put("route_hold_ms",routeHoldMs);
            SystemClock.sleep(routeHoldMs);
            long began=SystemClock.elapsedRealtime(),next=began,frame=0;
            int upOffset=480,downOffset=480;
            while(SystemClock.elapsedRealtime()-began<6000) {
                long now=SystemClock.elapsedRealtime(),elapsed=now-began;
                if(stop.get() || now-checkedAt.get()>2000) throw new IOException("Banc arrêté par protection");
                if(!same(injected.getRoutedDevice(),simulatedMic.getRoutedDevice())
                        || !same(simulatedSpeaker.getRoutedDevice(),extracted.getRoutedDevice()))
                    throw new IOException("Route virtuelle modifiée");
                if(now>=next && upOffset==480 && downOffset==480) {
                    boolean signal=elapsed>=1200;
                    for(int i=0;i<480;i++) {
                        upSignal[i]=signal?(short)(4000*Math.sin(2*Math.PI*1000*(frame+i)/24000)):0;
                        downSignal[i]=signal?(short)(4000*Math.sin(2*Math.PI*1500*(frame+i)/24000)):0;
                        upBytes[2*i]=(byte)upSignal[i]; upBytes[2*i+1]=(byte)(upSignal[i]>>8);
                    }
                    frame+=480; upOffset=downOffset=0; next+=20;
                    if(now-next>300) throw new IOException("Ordonnancement PCM en retard");
                }
                if(upOffset<480) {
                    int n;
                    if(byteWrites) {
                        n=injected.write(upBytes,2*upOffset,2*(480-upOffset),AudioTrack.WRITE_NON_BLOCKING);
                        if(n<0 || (n&1)!=0 || n>2*(480-upOffset)) throw new IOException("Injection octets refusée");
                        n/=2;
                    } else n=injected.write(upSignal,upOffset,480-upOffset,AudioTrack.WRITE_NON_BLOCKING);
                    if(n<0 || n>480-upOffset) throw new IOException("Injection refusée"); upOffset+=n; upWritten+=n;
                }
                if(downOffset<480) { int n=simulatedSpeaker.write(downSignal,downOffset,480-downOffset,AudioTrack.WRITE_NON_BLOCKING); if(n<0) throw new IOException("Source simulée refusée"); downOffset+=n; downWritten+=n; }
                int u=simulatedMic.read(upInput,0,upInput.length,AudioRecord.READ_NON_BLOCKING);
                int d=extracted.read(downInput,0,downInput.length,AudioRecord.READ_NON_BLOCKING);
                if(u<0 || d<0) throw new IOException("Lecture simulée refusée");
                upRead+=u; downRead+=d;
                if(elapsed<1000) { for(int i=0;i<u;i++) quietUp.add(upInput[i]); for(int i=0;i<d;i++) quietDown.add(downInput[i]); }
                if(elapsed>1800) { for(int i=0;i<u;i++) up.add(upInput[i]); for(int i=0;i<d;i++) down.add(downInput[i]); }
                Arrays.fill(upInput,(short)0); Arrays.fill(downInput,(short)0);
                SystemClock.sleep(1);
            }
            result.put("injection_route_matches",same(injected.getRoutedDevice(),simulatedMic.getRoutedDevice()))
                .put("extraction_route_matches",same(simulatedSpeaker.getRoutedDevice(),extracted.getRoutedDevice()))
                .put("injector_head",Integer.toUnsignedLong(injected.getPlaybackHeadPosition()))
                .put("source_head",Integer.toUnsignedLong(simulatedSpeaker.getPlaybackHeadPosition()))
                .put("injector_underruns",injected.getUnderrunCount())
                .put("source_underruns",simulatedSpeaker.getUnderrunCount());
            guard(inspector,audio,mode);
            boolean passed=!stop.get() && guardFailure.get().isEmpty() && up.hits>=4 && down.hits>=4 && quietUp.windows>0 && quietDown.windows>0
                && quietUp.hits==0 && quietDown.hits==0 && result.getBoolean("injection_route_matches")
                && result.getBoolean("extraction_route_matches");
            result.put("state",passed?"passed":"signal_not_verified");
        } catch(Exception e) { try { result.put("state","failed").put("error_type",e.getClass().getSimpleName()).put("error",e.getMessage()); } catch(Exception ignored) {} }
        finally {
            stop.set(true);
            for(AudioRecord r:new AudioRecord[]{simulatedMic,extracted}) if(r!=null) { try { r.stop(); }catch(Exception ignored) {} r.release(); }
            for(AudioTrack t:new AudioTrack[]{simulatedSpeaker,injected}) if(t!=null) { try { t.pause(); t.flush(); }catch(Exception ignored) {} t.release(); }
            if(monitor!=null) try { monitor.join(2500); }catch(InterruptedException ignored) {}
            if(monitor!=null && monitor.isAlive()) guardFailure.set("monitor_timeout");
            if(ownsMode) audio.setMode(AudioManager.MODE_NORMAL); // removes this process's mode request only
            Arrays.fill(upSignal,(short)0); Arrays.fill(downSignal,(short)0);
            Arrays.fill(upBytes,(byte)0);
            Arrays.fill(upInput,(short)0); Arrays.fill(downInput,(short)0);
            try { if(!guardFailure.get().isEmpty()) result.put("state","failed");
                result.put("stage",stage).put("tested_mode",mode).put("guard_failure_type",guardFailure.get())
                .put("injection_write_api",byteWrites?"byte[] PCM16LE":"short[] PCM16")
                .put("uplink",evidence(up,quietUp,upWritten,upRead)).put("downlink",evidence(down,quietDown,downWritten,downRead))
                .put("mode_after",audio.getMode()).put("synthetic_only",true).put("audio_saved",false)
                .put("whatsapp_verified",false); } catch(Exception ignored) {}
            done.set(true);
        }
        return result;
    }
    private static AudioFormat format(int rate,boolean input) { return new AudioFormat.Builder().setSampleRate(rate)
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(input?AudioFormat.CHANNEL_IN_MONO:AudioFormat.CHANNEL_OUT_MONO).build(); }
    private static boolean same(AudioDeviceInfo a,AudioDeviceInfo b) {
        return a!=null && b!=null && a.getType()==25 && b.getType()==25 && !a.getAddress().isEmpty() && a.getAddress().equals(b.getAddress());
    }
    private static JSONObject evidence(ToneEvidence active,ToneEvidence quiet,long written,long read) throws JSONException {
        return new JSONObject().put("tone_windows",active.hits).put("windows",active.windows)
            .put("last_rms",active.lastRms).put("last_tone_fraction",active.lastFraction)
            .put("silence_windows",quiet.windows).put("silence_false_positives",quiet.hits)
            .put("written_samples",written).put("read_samples",read);
    }
    private static void guard(VoipAudioTest inspector,AudioManager audio,int mode) throws Exception {
        guard(inspector,audio,mode,2000);
    }
    static void guard(VoipAudioTest inspector,AudioManager audio,int mode,int peerUid) throws Exception {
        JSONObject d=inspector.inspectOwner();
        String expected=mode==3?"MODE_IN_COMMUNICATION":"MODE_COMMUNICATION_REDIRECT";
        if(!d.optBoolean("no_current_calls_verified") || !"2000".equals(d.optString("owner_uid"))
            || !Integer.toString(android.os.Process.myPid()).equals(d.optString("owner_pid"))
            || !"com.android.shell".equals(d.optString("owner_package")) || !expected.equals(d.optString("actual_mode")))
            throw new IOException("Appel ou propriétaire extérieur détecté");
        for(AudioRecordingConfiguration c:audio.getActiveRecordingConfigurations())
            if((Integer)AudioRecordingConfiguration.class.getMethod("getClientUid").invoke(c)!=2000
                && (Integer)AudioRecordingConfiguration.class.getMethod("getClientUid").invoke(c)!=peerUid)
                throw new IOException("Enregistrement extérieur détecté");
    }
}
