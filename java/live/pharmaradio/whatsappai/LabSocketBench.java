package live.pharmaradio.whatsappai;
import android.os.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** Synthetic PCM only; no audio devices, calls, network, or file storage. */
final class LabSocketBench implements Runnable {
    static final String DESCRIPTOR="live.pharmaradio.whatsappai.LocalSocketBench.v1";
    private static volatile LabSocketBench active;
    static final Binder CONTROL=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
            if(Binder.getCallingUid()!=2000)throw new SecurityException("Shell only");
            data.enforceInterface(DESCRIPTOR);
            long identity=Binder.clearCallingIdentity();
            try{synchronized(LabSocketBench.class){
                if(code==1){
                    ParcelFileDescriptor fd=data.readTypedObject(ParcelFileDescriptor.CREATOR);
                    if(fd==null)throw new IllegalArgumentException("Missing channel");
                    if(active!=null&&!active.done){try{fd.close();}catch(IOException ignored){}throw new IllegalStateException("Bench busy");}
                    active=new LabSocketBench(fd);new Thread(active,"lab-synthetic-socket").start();
                }else if(code==2){
                    if(active==null||active.done||active.goAt!=0)throw new IllegalStateException("Not ready");
                    long at=data.readLong(),now=SystemClock.elapsedRealtime();
                    if(at<now||at>now+1000)throw new IllegalArgumentException("Invalid start");active.goAt=at;
                }else if(code==4){if(active!=null)active.cancel();}
                else if(code!=3)return false;
                reply.writeNoException();reply.writeString(active==null?"{\"state\":\"idle\"}":active.report);return true;
            }}finally{Binder.restoreCallingIdentity(identity);}
        }
    };
    final PcmSocket wire;final AtomicBoolean stop=new AtomicBoolean();
    final AtomicReference<String> failure=new AtomicReference<>("");
    final AtomicLong received=new AtomicLong(),sent=new AtomicLong();
    volatile boolean done;volatile long goAt;
    volatile String report="{\"state\":\"starting\"}";
    LabSocketBench(ParcelFileDescriptor fd){wire=new PcmSocket(fd);}
    void cancel(){stop.set(true);wire.close();}
    @Override public void run(){
        ToneEvidence down=new ToneEvidence(24000,1500),quiet=new ToneEvidence(24000,1500);
        Thread receiver=null;byte[] frame=new byte[PcmProtocol.FRAME];
        final long deadline=SystemClock.elapsedRealtime()+12000;
        Thread watchdog=new Thread(()->{SystemClock.sleep(12000);if(!done){failure.compareAndSet("","deadline");cancel();}},"lab-socket-deadline");watchdog.start();
        try{
            report=new JSONObject().put("state","ready").put("uid",android.os.Process.myUid()).toString();
            while(goAt==0||SystemClock.elapsedRealtime()<goAt){if(stop.get()||SystemClock.elapsedRealtime()>=deadline)throw new IOException("Start cancelled");SystemClock.sleep(1);}
            receiver=new Thread(()->{
                try{while(!stop.get()){
                    byte[] pcm=wire.read(1500);
                    try{long elapsed=SystemClock.elapsedRealtime()-goAt;
                        for(int i=0;i<pcm.length;i+=2){short sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));if(elapsed<1000)quiet.add(sample);if(elapsed>1800)down.add(sample);}
                        received.addAndGet(pcm.length);
                    }finally{Arrays.fill(pcm,(byte)0);}
                }}catch(Exception e){if(!stop.get())failure.compareAndSet("",e.getClass().getSimpleName());}
            },"lab-socket-downlink");receiver.start();
            long next=goAt,sample=0;
            while(!stop.get()&&failure.get().isEmpty()&&SystemClock.elapsedRealtime()<deadline){
                long now=SystemClock.elapsedRealtime(),elapsed=now-goAt;
                if(elapsed>=6500){SystemClock.sleep(5);continue;}
                if(now<next){SystemClock.sleep(1);continue;}
                if(now-next>300)throw new IOException("Generator late");
                for(int i=0;i<480;i++,sample++){short v=elapsed>=1200?(short)(4000*Math.sin(2*Math.PI*1000*sample/24000)):0;frame[2*i]=(byte)v;frame[2*i+1]=(byte)(v>>8);}
                wire.write(frame,frame.length,500);sent.addAndGet(frame.length);next+=20;
            }
            if(SystemClock.elapsedRealtime()>=deadline)failure.compareAndSet("","deadline");
        }catch(Exception e){if(!stop.get())failure.compareAndSet("",e.getClass().getSimpleName());}
        finally{
            cancel();if(receiver!=null)try{receiver.join(2000);}catch(InterruptedException ignored){}
            if(receiver!=null&&receiver.isAlive())failure.compareAndSet("","receiver_timeout");Arrays.fill(frame,(byte)0);
            try{report=new JSONObject().put("state",failure.get().isEmpty()&&sent.get()>0&&received.get()>0&&down.hits>=4&&quiet.windows>0&&quiet.hits==0?"passed":"failed")
                .put("uid",android.os.Process.myUid()).put("error",failure.get()).put("sent_bytes",sent.get()).put("received_bytes",received.get())
                .put("tone_windows",down.hits).put("windows",down.windows).put("silence_windows",quiet.windows).put("silence_false_positives",quiet.hits)
                .put("last_rms",down.lastRms).put("last_tone_fraction",down.lastFraction).put("audio_saved",false).put("whatsapp_verified",false).toString();
            }catch(Exception ignored){}done=true;
        }
    }
}
