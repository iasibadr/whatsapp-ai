package live.pharmaradio.whatsappai;
import java.nio.ByteBuffer;
final class PcmProtocol {
    static final int RATE=24000, FRAME=960, STOP=-1, HEARTBEAT=-3;
    static boolean valid(int n) { return n==STOP || n==HEARTBEAT || n>0 && n<=FRAME && (n&1)==0; }
    static byte[] header(int n) { if(!valid(n)) throw new IllegalArgumentException("Trame PCM invalide"); return ByteBuffer.allocate(4).putInt(n).array(); }
    static int length(byte[] header) { if(header.length!=4) throw new IllegalArgumentException("En-tête invalide"); return ByteBuffer.wrap(header).getInt(); }
}
