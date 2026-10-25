package live.pharmaradio.whatsappai;
import java.util.Arrays;
final class SpeechFrames {
    static int fill(byte[] frame,byte[] speech,long firstSample){
        if(frame.length!=960||speech.length==0||(speech.length&1)!=0||speech.length>960000)throw new IllegalArgumentException("Invalid speech PCM");
        Arrays.fill(frame,(byte)0);
        if(firstSample<=-480||firstSample>=speech.length/2)return 0;
        int target=firstSample<0?(int)(-firstSample*2):0;
        int source=firstSample<0?0:(int)(firstSample*2);
        int count=Math.min(frame.length-target,speech.length-source);
        System.arraycopy(speech,source,frame,target,count);return count;
    }
}
