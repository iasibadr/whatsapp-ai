package live.pharmaradio.whatsappai;
import java.util.Arrays;
/** Bounded PCM ring, even-byte chunks only. Consumed and discarded audio is wiped. */
final class AudioQueue {
    private final byte[] data; private int head,size;
    AudioQueue(int capacity){if(capacity<=0||(capacity&1)!=0)throw new IllegalArgumentException();data=new byte[capacity];}
    synchronized void add(byte[] chunk){
        if((chunk.length&1)!=0)throw new IllegalArgumentException();
        if(chunk.length>data.length-size)throw new IllegalStateException("Audio queue full");
        for(byte b:chunk){data[(head+size)%data.length]=b;size++;}
    }
    synchronized int drain(byte[] out){
        if((out.length&1)!=0)throw new IllegalArgumentException();
        Arrays.fill(out,(byte)0);int n=Math.min(size,out.length);
        for(int i=0;i<n;i++){out[i]=data[head];data[head]=0;head=(head+1)%data.length;size--;}
        return n;
    }
    synchronized int size(){return size;}
    synchronized void clear(){Arrays.fill(data,(byte)0);size=0;head=0;}
}
