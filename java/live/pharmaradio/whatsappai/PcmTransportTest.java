package live.pharmaradio.whatsappai;
import android.os.*;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.atomic.*;
import org.json.JSONObject;

/** App-side PCM consumer/producer. Counts downlink only, never saves or echoes speech. */
final class PcmTransportTest {
    interface Listener { void result(String report); }
    private volatile PcmSocket wire;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    void cancel() { cancelled.set(true); PcmSocket w=wire; if(w!=null) w.close(); }
    static String command(IBinder binder,int code) throws Exception {
        Parcel req=Parcel.obtain(),reply=Parcel.obtain();
        try { req.writeInterfaceToken(BridgeProvider.DESCRIPTOR);
            if(!binder.transact(code,req,reply,0)) throw new IOException("Transport incompatible");
            reply.readException(); return code==10||code==1||code==15?reply.readString():"";
        } finally { req.recycle(); reply.recycle(); }
    }
    void run(IBinder binder,Listener listener) {
        run(binder,listener,null);
    }
    void run(IBinder binder,Listener listener,byte[] speech) {
        AtomicLong received=new AtomicLong(),sent=new AtomicLong();
        AtomicReference<String> failure=new AtomicReference<>("");
        AtomicBoolean finished=new AtomicBoolean();
        Thread receiver=null;
        final long[] energy=new long[1]; final int[] peak=new int[1];
        final int[] beepFrames=new int[3];
        String helper="{}";
        long speechSent=0;
        long begin=SystemClock.elapsedRealtime();
        try {
            if(speech!=null)SpeechFrames.fill(new byte[960],speech,-480);
            Parcel req=Parcel.obtain(),reply=Parcel.obtain();
            try { req.writeInterfaceToken(BridgeProvider.DESCRIPTOR); req.writeString("whatsapp-pcm-thirty-seconds-consented");
                if(!binder.transact(9,req,reply,0)) throw new IOException("Transport incompatible");
                reply.readException(); ParcelFileDescriptor fd=reply.readTypedObject(ParcelFileDescriptor.CREATOR);
                if(fd==null) throw new IOException("Canal absent"); wire=new PcmSocket(fd);
            } finally { req.recycle(); reply.recycle(); }
            if(cancelled.get()) throw new IOException("Annulé avant ouverture");
            PcmSocket channel=wire;
            // Wait for the helper to finish guard + route initialization before streaming.
            while(!cancelled.get()) {
                helper=command(binder,10); String state=new JSONObject(helper).optString("state");
                if("running".equals(state)) break;
                if(!"starting".equals(state) || SystemClock.elapsedRealtime()-begin>7000) throw new IOException("Initialisation PCM refusée");
                Thread.sleep(50);
            }
            begin=SystemClock.elapsedRealtime();
            receiver=new Thread(() -> {
                try {
                    while(!finished.get() && !cancelled.get()) {
                        byte[] b=channel.read(1500);
                        for(int i=0;i<b.length;i+=2) { int v=(short)((b[i]&255)|(b[i+1]<<8)); peak[0]=Math.max(peak[0],Math.abs(v)); energy[0]+=(long)v*v; }
                        received.addAndGet(b.length); Arrays.fill(b,(byte)0);
                    }
                } catch(Exception e) { if(!finished.get() && !cancelled.get()) failure.compareAndSet("","réception: "+e.getClass().getSimpleName()); }
            },"apk-downlink-consumer"); receiver.start();
            byte[] frame=new byte[PcmProtocol.FRAME];
            long next=begin, sample=0;
            try {
                while(!cancelled.get() && failure.get().isEmpty() && SystemClock.elapsedRealtime()-begin<29000) {
                    long now=SystemClock.elapsedRealtime(),ms=now-begin;
                    if(now<next) { Thread.sleep(Math.min(20,next-now)); continue; }
                    if(now-next>100) throw new IOException("Production PCM en retard");
                    int frequency=ms>=2000 && ms<2300?440:ms>=12000 && ms<12300?660:ms>=22000 && ms<22300?880:0;
                    int speechCount=0;
                    if(speech!=null){frequency=0;speechCount=SpeechFrames.fill(frame,speech,sample-48000);sample+=480;}
                    else for(int i=0;i<480;i++,sample++) {
                        short v=frequency==0?0:(short)(1800*Math.sin(2*Math.PI*frequency*sample/24000)*Math.sin(Math.PI*i/480));
                        frame[2*i]=(byte)v; frame[2*i+1]=(byte)(v>>8);
                    }
                    channel.write(frame,frame.length,500); sent.addAndGet(frame.length); next+=20;
                    speechSent+=speechCount;
                    if(frequency!=0) beepFrames[frequency==440?0:frequency==660?1:2]++;
                }
            } finally { Arrays.fill(frame,(byte)0); }
        } catch(Exception e) { if(!cancelled.get()) failure.compareAndSet("",e.getClass().getSimpleName()+": "+e.getMessage()); }
        finally {
            finished.set(true);
            // Binder stop precedes close, avoiding a normal client stop appearing as peer failure.
            try { command(binder,11); } catch(Exception ignored) {}
            if(wire!=null) wire.close();
            if(receiver!=null) try { receiver.join(2000); } catch(InterruptedException ignored) {}
            try {
                for(int i=0;i<40;i++) { helper=command(binder,10); String s=new JSONObject(helper).optString("state");
                    if(!"running".equals(s) && !"starting".equals(s)) break; Thread.sleep(100); }
                JSONObject server=new JSONObject(helper);
                boolean ok=failure.get().isEmpty() && !cancelled.get() && received.get()>0 && sent.get()>0
                    && (speech==null||speechSent==speech.length)
                    && server.optBoolean("routes_started") && "stopped".equals(server.optString("state")) && server.optString("error").isEmpty();
                listener.result(new JSONObject().put("state",cancelled.get()?"cancelled":ok?"transport_completed":"failed")
                    .put("app_error",failure.get()).put("app_received_bytes",received.get()).put("app_sent_bytes",sent.get())
                    .put("app_peak",peak[0]).put("app_rms",received.get()==0?0:Math.sqrt(energy[0]/(received.get()/2.0)))
                    .put("beep1_frames_sent",beepFrames[0]).put("beep2_frames_sent",beepFrames[1]).put("beep3_frames_sent",beepFrames[2])
                    .put("stimulus",speech==null?"three_beeps":"spoken_french_phrase").put("speech_pcm_bytes_sent",speechSent)
                    .put("speech_pcm_bytes_expected",speech==null?0:speech.length)
                    .put("helper",server).put("audio_saved",false).put("human_audio_verified",false).toString(2));
            } catch(Exception e) { listener.result("Transport non validé : "+e.getClass().getSimpleName()); }
        }
    }
}
