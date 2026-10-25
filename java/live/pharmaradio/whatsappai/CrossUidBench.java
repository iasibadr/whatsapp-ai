package live.pharmaradio.whatsappai;
import android.content.Context;
import android.media.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.nio.channels.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.*;
import org.json.*;
final class CrossUidBench {
    static final String PEER="live.pharmaradio.voiptestpeer",DESCRIPTOR=PEER+".test.v1";
    static JSONObject rpc(IBinder b,int code,int effects,long at,String... routes)throws Exception {
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try{request.writeInterfaceToken(DESCRIPTOR);if(code==1){request.writeInt(effects);request.writeString(routes[0]);request.writeString(routes[1]);}if(code==2)request.writeLong(at);
            if(!b.transact(code,request,reply,0))throw new IOException("Peer protocol refused");reply.readException();return new JSONObject(reply.readString());
        }finally{request.recycle();reply.recycle();}
    }
    static String hash(AudioDeviceInfo d)throws Exception {
        if(d==null||d.getAddress().isEmpty())return "";
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(d.getAddress().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format("%02x",b&255));return s.toString();
    }
    static JSONObject run(Context context,AudioManager audio,int mode,boolean effects){
        return run(context,audio,mode,effects,false);
    }
    static JSONObject labRpc(IBinder b,int code,long at,ParcelFileDescriptor fd)throws Exception {
        Parcel request=Parcel.obtain(),reply=Parcel.obtain();
        try{request.writeInterfaceToken(LabSocketBench.DESCRIPTOR);if(code==1)request.writeTypedObject(fd,0);if(code==2)request.writeLong(at);
            if(!b.transact(code,request,reply,0))throw new IOException("Lab protocol refused");reply.readException();return new JSONObject(reply.readString());
        }finally{request.recycle();reply.recycle();}
    }
    static JSONObject run(Context context,AudioManager audio,int mode,boolean effects,boolean privatePcm){
        JSONObject result=new JSONObject();String stage="preconditions";
        IBinder lab=null;PcmSocket wire=null;long socketReceived=0,socketSent=0;
        AudioRecord record=null;AudioTrack track=null;IBinder peer=null;Thread monitor=null,warmup=null;
        AtomicBoolean warming=new AtomicBoolean(true);AtomicLong warmupSamples=new AtomicLong();
        AtomicBoolean done=new AtomicBoolean(),stop=new AtomicBoolean();AtomicLong checked=new AtomicLong();
        AtomicReference<String> guardError=new AtomicReference<>("");boolean owns=false;
        ToneEvidence down=new ToneEvidence(24000,1500),quiet=new ToneEvidence(24000,1500);
        byte[] out=new byte[960];short[] in=new short[4800];long written=0,read=0;
        VoipAudioTest inspector=new VoipAudioTest(context,audio);
        try(FileOutputStream f=new FileOutputStream("/data/local/tmp/whatsapp-relay-local-bench.lock",true);
            FileChannel channel=f.getChannel();FileLock lock=channel.tryLock()){
            if(lock==null)throw new IOException("Bench busy");
            JSONObject before=inspector.inspectOwner();
            if(!before.optBoolean("no_current_calls_verified")||audio.getMode()!=0||!before.optBoolean("owner_none")||!audio.getActiveRecordingConfigurations().isEmpty())throw new IOException("Audio or call already active");
            int peerUid=context.getPackageManager().getApplicationInfo(PEER,0).uid;
            if(peerUid<10000||peerUid==android.os.Process.myUid())throw new IOException("Peer UID not independent");
            result.put("helper_uid",android.os.Process.myUid()).put("peer_uid",peerUid);
            new Thread(()->{SystemClock.sleep(22000);if(!done.get())System.exit(2);},"cross-uid-deadline").start();
            if(privatePcm){
                stage="lab_socket_prepare";
                Bundle control=providerControl(BridgeProvider.AUTHORITY,"local-socket-bench");
                if(control==null||(lab=control.getBinder("endpoint"))==null)throw new IOException("Lab missing");
                ParcelFileDescriptor[] pair=ParcelFileDescriptor.createSocketPair();
                try{PcmSocket.nonblocking(pair[0]);PcmSocket.nonblocking(pair[1]);wire=new PcmSocket(pair[0]);labRpc(lab,1,0,pair[1]);}
                finally{pair[1].close();if(wire==null)pair[0].close();}
                long until=SystemClock.elapsedRealtime()+1500;JSONObject ready;
                do{ready=labRpc(lab,3,0,null);if(!"starting".equals(ready.optString("state")))break;SystemClock.sleep(10);}while(SystemClock.elapsedRealtime()<until);
                int labUid=context.getPackageManager().getApplicationInfo("live.pharmaradio.whatsappai",0).uid;
                if(!"ready".equals(ready.optString("state"))||ready.optInt("uid")!=labUid||labUid==peerUid||labUid==2000)throw new IOException("Lab identity unavailable");
                result.put("lab_uid",labUid).put("private_pcm_socket",true);
            }
            audio.setMode(mode);owns=true;LocalAudioBench.guard(inspector,audio,mode,peerUid);
            stage="relay_routes";
            AudioFormat input=new AudioFormat.Builder().setSampleRate(24000).setEncoding(2).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            AudioFormat output=new AudioFormat.Builder().setSampleRate(24000).setEncoding(2).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            AudioRecord.Builder rb=new AudioRecord.Builder().setAudioFormat(input);
            AudioRecord.Builder.class.getMethod("setContext",Context.class).invoke(rb,context);AudioRecord.Builder.class.getMethod("setCallRedirectionMode",int.class).invoke(rb,2);record=rb.build();
            AudioTrack.Builder tb=new AudioTrack.Builder().setAudioFormat(output).setTransferMode(AudioTrack.MODE_STREAM);
            AudioTrack.Builder.class.getMethod("setContext",Context.class).invoke(tb,context);AudioTrack.Builder.class.getMethod("setCallRedirectionMode",int.class).invoke(tb,2);track=tb.build();
            record.startRecording();track.play();
            checked.set(SystemClock.elapsedRealtime());
            monitor=new Thread(()->{try{while(!stop.get()){LocalAudioBench.guard(inspector,audio,mode,peerUid);checked.set(SystemClock.elapsedRealtime());SystemClock.sleep(250);}}catch(Exception e){guardError.set(e.getClass().getSimpleName());stop.set(true);}},"cross-uid-no-call-monitor");monitor.start();
            final AudioTrack warmingTrack=track;
            warmup=new Thread(()->{
                byte[] silence=new byte[960];long next=SystemClock.elapsedRealtime();int offset=960;
                try{while(warming.get()&&!stop.get()){
                    long now=SystemClock.elapsedRealtime();
                    if(now-checked.get()>2000)throw new IOException("Warmup guard expired");
                    AudioDeviceInfo device=warmingTrack.getRoutedDevice();
                    if(device==null||device.getType()!=25)throw new IOException("Warmup route missing");
                    if(now>=next&&offset==960){offset=0;next+=20;}
                    if(offset<960){int n=warmingTrack.write(silence,offset,960-offset,AudioTrack.WRITE_NON_BLOCKING);
                        if(n<0||n>960-offset||(n&1)!=0)throw new IOException("Warmup write failed");offset+=n;warmupSamples.addAndGet(n/2);}
                    SystemClock.sleep(1);
                }}catch(Exception e){guardError.set("warmup_failed");stop.set(true);}
            },"cross-uid-silence-warmup");warmup.start();
            stage="peer_prepare";
            java.lang.Process launch=new ProcessBuilder("/system/bin/am","start","-n",PEER+"/.PeerActivity").redirectErrorStream(true).start();
            try {if(!launch.waitFor(2,TimeUnit.SECONDS)||launch.exitValue()!=0)throw new IOException("Peer launch failed");}finally{launch.destroy();}
            SystemClock.sleep(250);
            Bundle control=providerControl(PEER+".control","control");
            if(control==null||(peer=control.getBinder("endpoint"))==null)throw new IOException("Peer missing");
            rpc(peer,1,effects?1:0,0,hash(track.getRoutedDevice()),hash(record.getRoutedDevice()));
            JSONObject ready;long readyUntil=SystemClock.elapsedRealtime()+3000;
            do{if(stop.get()||SystemClock.elapsedRealtime()>readyUntil)throw new IOException("Peer prepare expired");ready=rpc(peer,3,0,0);SystemClock.sleep(20);}while("starting".equals(ready.optString("state")));
            if(!"ready".equals(ready.optString("state"))){ready.remove("input_hash");ready.remove("output_hash");result.put("peer",ready);throw new IOException("Peer initialization refused: "+ready.optString("error_type"));}
            AudioDeviceInfo td=track.getRoutedDevice(),rd=record.getRoutedDevice();
            if(ready.optInt("uid")!=peerUid||!BenchRoutes.matches(td==null?-1:td.getType(),hash(td),ready.optInt("input_type"),ready.optString("input_hash"))
                ||!BenchRoutes.matches(rd==null?-1:rd.getType(),hash(rd),ready.optInt("output_type"),ready.optString("output_hash")))throw new IOException("Cross UID virtual route mismatch");
            int ti=td.getId(),ri=rd.getId();result.put("injection_route_matches",true).put("extraction_route_matches",true);
            LocalAudioBench.guard(inspector,audio,mode,peerUid);checked.set(SystemClock.elapsedRealtime());
            long start=SystemClock.elapsedRealtime()+150;rpc(peer,2,0,start);if(privatePcm)labRpc(lab,2,start,null);while(SystemClock.elapsedRealtime()<start)SystemClock.sleep(1);
            warming.set(false);warmup.join(1000);if(warmup.isAlive())throw new IOException("Warmup did not stop");
            stage="synthetic_pcm";long next=start,frame=0,nextPeerCheck=start;int offset=960;
            while(SystemClock.elapsedRealtime()-start<6000){
                long now=SystemClock.elapsedRealtime(),elapsed=now-start;
                if(stop.get()||now-checked.get()>2000)throw new IOException("Call protection stopped bench");
                if(now>=nextPeerCheck){
                    String peerState=rpc(peer,3,0,0).optString("state");
                    if(!"ready".equals(peerState)&&!"passed".equals(peerState))throw new IOException("Peer stopped; bench cancelled");
                    nextPeerCheck=now+200;
                }
                td=track.getRoutedDevice();rd=record.getRoutedDevice();if(td==null||rd==null||td.getId()!=ti||rd.getId()!=ri)throw new IOException("Relay route changed");
                if(privatePcm&&offset==960){
                    byte[] pcm=wire.read(500);try{if(pcm.length!=960)throw new IOException("Expected full synthetic frame");System.arraycopy(pcm,0,out,0,960);socketReceived+=960;}finally{Arrays.fill(pcm,(byte)0);}
                    offset=0;
                    now=SystemClock.elapsedRealtime();elapsed=now-start;
                    if(stop.get()||now-checked.get()>2000)throw new IOException("Call protection stopped socket bench");
                    td=track.getRoutedDevice();rd=record.getRoutedDevice();if(td==null||rd==null||td.getId()!=ti||rd.getId()!=ri)throw new IOException("Relay route changed after socket wait");
                }
                if(!privatePcm&&now>=next&&offset==960){for(int i=0;i<480;i++){short v=elapsed>=1200?(short)(4000*Math.sin(2*Math.PI*1000*(frame+i)/24000)):0;out[2*i]=(byte)v;out[2*i+1]=(byte)(v>>8);}frame+=480;offset=0;next+=20;if(now-next>300)throw new IOException("PCM schedule late");}
                if(offset<960){int n=track.write(out,offset,960-offset,AudioTrack.WRITE_NON_BLOCKING);if(n<0||n>960-offset||(n&1)!=0)throw new IOException("Injection write failed");offset+=n;written+=n/2;}
                int n=record.read(in,0,privatePcm?480:in.length,AudioRecord.READ_NON_BLOCKING);if(n<0)throw new IOException("Extraction failed");read+=n;
                if(privatePcm&&n>0){byte[] pcm=new byte[n*2];try{for(int i=0;i<n;i++){pcm[2*i]=(byte)in[i];pcm[2*i+1]=(byte)(in[i]>>8);}wire.write(pcm,pcm.length,500);socketSent+=pcm.length;}finally{Arrays.fill(pcm,(byte)0);}}
                for(int i=0;i<n;i++){if(elapsed<1000)quiet.add(in[i]);if(elapsed>1800)down.add(in[i]);}Arrays.fill(in,(short)0);SystemClock.sleep(1);
            }
            stage="peer_result";long resultUntil=SystemClock.elapsedRealtime()+1500;JSONObject pr;
            do{pr=rpc(peer,3,0,0);if(!"ready".equals(pr.optString("state")))break;SystemClock.sleep(20);}while(SystemClock.elapsedRealtime()<resultUntil);
            pr.remove("input_hash");pr.remove("output_hash");result.put("peer",pr);
            LocalAudioBench.guard(inspector,audio,mode,peerUid);
            result.put("injector_head",Integer.toUnsignedLong(track.getPlaybackHeadPosition())).put("injector_underruns",track.getUnderrunCount());
            result.put("state",!stop.get()&&guardError.get().isEmpty()&&"passed".equals(pr.optString("state"))&&down.hits>=4&&quiet.windows>0&&quiet.hits==0?"passed":"signal_not_verified");
        }catch(Exception e){try{result.put("state","failed").put("error_type",e.getClass().getSimpleName()).put("error",e.getMessage());}catch(Exception ignored){}}
        finally{
            stop.set(true);
            if(peer!=null)try{rpc(peer,4,0,0);}catch(Exception e){guardError.set("peer_stop_unconfirmed");}
            if(lab!=null)try{
                labRpc(lab,4,0,null);long until=SystemClock.elapsedRealtime()+2200;JSONObject lr;
                do{lr=labRpc(lab,3,0,null);if(!"starting".equals(lr.optString("state"))&&!"ready".equals(lr.optString("state")))break;SystemClock.sleep(20);}while(SystemClock.elapsedRealtime()<until);
                result.put("lab",lr);if(!"passed".equals(lr.optString("state")))result.put("state","failed");
            }catch(Exception e){guardError.set("lab_cleanup_unconfirmed");}
            if(wire!=null)wire.close();
            warming.set(false);if(warmup!=null)try{warmup.join(1000);}catch(InterruptedException ignored){}
            if(warmup!=null&&warmup.isAlive())guardError.set("warmup_timeout");
            if(peer!=null)try{
                long until=SystemClock.elapsedRealtime()+1000;
                while(SystemClock.elapsedRealtime()<until){String state=rpc(peer,3,0,0).optString("state");if(!"starting".equals(state)&&!"ready".equals(state))break;SystemClock.sleep(20);}
                JSONObject lastPeer=rpc(peer,3,0,0);String state=lastPeer.optString("state");
                lastPeer.remove("input_hash");lastPeer.remove("output_hash");result.put("peer",lastPeer);
                if("starting".equals(state)||"ready".equals(state)){
                    guardError.set("peer_cleanup_timeout");
                    java.lang.Process kill=new ProcessBuilder("/system/bin/am","force-stop",PEER).start();kill.waitFor(2,TimeUnit.SECONDS);kill.destroy();
                }
            }catch(Exception e){guardError.set("peer_cleanup_unconfirmed");}
            if(record!=null){try{record.stop();}catch(Exception ignored){}record.release();}if(track!=null){try{track.pause();track.flush();}catch(Exception ignored){}track.release();}
            if(monitor!=null)try{monitor.join(2500);}catch(InterruptedException ignored){}
            if(monitor!=null&&monitor.isAlive())guardError.set("monitor_timeout");
            if(owns)audio.setMode(0);Arrays.fill(out,(byte)0);Arrays.fill(in,(short)0);
            try{if(!guardError.get().isEmpty())result.put("state","failed");result.put("stage",stage).put("tested_mode",mode).put("effects_requested",effects)
                .put("guard_failure_type",guardError.get()).put("tone_windows",down.hits).put("windows",down.windows)
                .put("last_rms",down.lastRms).put("last_tone_fraction",down.lastFraction).put("silence_windows",quiet.windows).put("silence_false_positives",quiet.hits)
                .put("socket_received_bytes",socketReceived).put("socket_sent_bytes",socketSent)
                .put("warmup_silence_samples",warmupSamples.get()).put("written_samples",written).put("read_samples",read).put("mode_after",audio.getMode()).put("whatsapp_verified",false).put("audio_saved",false);
            }catch(Exception ignored){}done.set(true);
        }
        return result;
    }
    private static Bundle providerControl(String authority,String method)throws Exception {
        IBinder token=new Binder();Object manager=null;
        try {
            Class<?> type=Class.forName("android.app.IActivityManager");
            manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            Object holder=type.getMethod("getContentProviderExternal",String.class,int.class,IBinder.class,String.class).invoke(manager,authority,0,token,"local-voip-bench");
            if(holder==null)throw new IOException("Peer provider absent");
            return (Bundle)Class.forName("android.content.IContentProvider")
                .getMethod("call",android.content.AttributionSource.class,String.class,String.class,String.class,Bundle.class)
                .invoke(holder.getClass().getField("provider").get(holder),new android.content.AttributionSource.Builder(2000).setPackageName("com.android.shell").build(),authority,method,null,null);
        } finally { if(manager!=null)try{Class.forName("android.app.IActivityManager").getMethod("removeContentProviderExternalAsUser",String.class,IBinder.class,int.class).invoke(manager,authority,token,0);}catch(Exception ignored){} }
    }
}
