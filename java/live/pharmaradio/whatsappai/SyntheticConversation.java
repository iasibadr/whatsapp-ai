package live.pharmaradio.whatsappai;
import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.util.Arrays;
import org.json.JSONObject;

/** Real network/VAD with synthetic input only; playback simulated in paced frames. */
final class SyntheticConversation {
    private final RealtimeAudio realtime;private long next;
    private String phase="starting";final JSONObject evidence=new JSONObject();
    JSONObject evidence()throws Exception{return evidence.put("phase",phase).put("last_synthetic_response",realtime.syntheticText());}
    SyntheticConversation(RealtimeAudio realtime){this.realtime=realtime;next=SystemClock.elapsedRealtime();}
    private void tick(byte[] input)throws Exception{
        realtime.check();long now=SystemClock.elapsedRealtime();if(next<now-100)next=now;
        if(now<next)Thread.sleep(next-now);
        realtime.append(input);byte[] output=new byte[960];realtime.output.drain(output);Arrays.fill(output,(byte)0);next+=20;
    }
    private void asset(Context c,String name)throws Exception{
        byte[] speech;try(InputStream in=c.getAssets().open(name)){speech=in.readNBytes(480001);}
        try{
            if(speech.length==0||speech.length>480000||(speech.length&1)!=0)throw new IOException("Synthetic asset invalid");
            for(int offset=0;offset<speech.length;offset+=960){byte[] frame=new byte[960];System.arraycopy(speech,offset,frame,0,Math.min(960,speech.length-offset));try{tick(frame);}finally{Arrays.fill(frame,(byte)0);}}
        }finally{Arrays.fill(speech,(byte)0);}
    }
    private void waitAnswer(int before,String word,String number)throws Exception{
        long end=SystemClock.elapsedRealtime()+18000;
        while(realtime.completedGeneration.get()<=before||realtime.output.size()!=0){if(SystemClock.elapsedRealtime()>end)throw new IOException("VAD response deadline");tick(new byte[960]);}
        if(!realtime.syntheticAnswerEquals(word,number))throw new IOException("Unexpected synthetic answer");
        for(int i=0;i<35;i++)tick(new byte[960]);
    }
    JSONObject run(Context c)throws Exception{
        phase="forced_token_limit";
        realtime.syntheticOutputLimit(32);int limitedBefore=realtime.limitedResponses.get();asset(c,"ai-long.pcm");long end=SystemClock.elapsedRealtime()+18000;
        while(realtime.limitedResponses.get()==limitedBefore||realtime.output.size()!=0){if(SystemClock.elapsedRealtime()>end)throw new IOException("Token limit not exercised");tick(new byte[960]);}
        phase="token_limit_recovery";realtime.syntheticOutputLimit(300);int before=realtime.created.get();asset(c,"ai-challenge.pcm");waitAnswer(before,"douze","12");
        evidence.put("token_limit_recovery",true).put("limited_responses",realtime.limitedResponses.get());
        phase="context";
        before=realtime.created.get();asset(c,"ai-context.pcm");waitAnswer(before,"huit","8");
        evidence.put("context_answer_12_then_8",true);phase="interruptible_response";
        asset(c,"ai-long.pcm");end=SystemClock.elapsedRealtime()+15000;
        while(realtime.output.size()<4800){if(SystemClock.elapsedRealtime()>end)throw new IOException("No interruptible output");tick(new byte[960]);}
        phase="interruption_recovery";before=realtime.created.get();asset(c,"ai-interrupt.pcm");waitAnswer(before,"douze","12");
        if(realtime.interruptions.get()<1||realtime.truncationsConfirmed.get()<1||realtime.speechTurns.get()<4)throw new IOException("Interruption not confirmed");
        phase="complete";
        return evidence.put("interruption_answer_12",true)
            .put("speech_turns",realtime.speechTurns.get()).put("interruptions",realtime.interruptions.get()).put("truncations_confirmed",realtime.truncationsConfirmed.get());
    }
}
