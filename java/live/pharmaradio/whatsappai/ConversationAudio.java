package live.pharmaradio.whatsappai;
/** Tracks only the current response; late deltas from interrupted items are rejected. */
final class ConversationAudio {
    static final class Cut {final String id;final int audioEndMs;Cut(String id,int ms){this.id=id;audioEndMs=ms;}}
    static final class Drained {final String id;final int bytes;Drained(String id,int bytes){this.id=id;this.bytes=bytes;}}
    private final AudioQueue queue=new AudioQueue(2880000);
    private String id="";private long submitted;private boolean blocked,closed,generating;
    synchronized void begin(String next){if(closed)return;queue.clear();id=next;submitted=0;blocked=false;generating=true;}
    synchronized boolean add(String item,byte[] bytes){if(closed||blocked||!id.equals(item))return false;queue.add(bytes);return true;}
    synchronized int drain(byte[] frame){return drainTagged(frame).bytes;}
    synchronized Drained drainTagged(byte[] frame){int n=queue.drain(frame);submitted+=n;return new Drained(id,n);}
    synchronized int size(){return queue.size();}
    synchronized boolean generating(){return generating;}
    synchronized void done(){generating=false;}
    synchronized Cut interrupt(){
        if(closed||blocked||id.isEmpty()||(!generating&&queue.size()==0))return null;
        blocked=true;generating=false;queue.clear();
        // Conservative estimate: allow for the PCM socket and Android output buffer.
        return new Cut(id,(int)Math.max(0,submitted/48-120));
    }
    synchronized void clear(){queue.clear();}
    synchronized void close(){closed=true;queue.clear();id="";submitted=0;generating=false;}
}
