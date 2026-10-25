package live.pharmaradio.whatsappai;
import android.os.*;
import android.system.*;
import java.io.*;
import java.net.SocketTimeoutException;
import java.util.Arrays;
final class PcmSocket implements AutoCloseable {
    private final ParcelFileDescriptor socket;
    private final java.util.concurrent.atomic.AtomicBoolean closed=new java.util.concurrent.atomic.AtomicBoolean();
    PcmSocket(ParcelFileDescriptor socket) { this.socket=socket; }
    static void nonblocking(ParcelFileDescriptor socket) throws Exception {
        FileDescriptor fd=socket.getFileDescriptor();
        Os.fcntlInt(fd,OsConstants.F_SETFL,Os.fcntlInt(fd,OsConstants.F_GETFL,0)|OsConstants.O_NONBLOCK);
    }
    private void transfer(byte[] b,int length,boolean write,long deadline) throws Exception {
        int offset=0;
        while(offset<length) {
            if(closed.get()) throw new EOFException("Canal PCM fermé");
            long left=deadline-SystemClock.elapsedRealtime();
            if(left<=0) throw new SocketTimeoutException("Canal PCM trop lent");
            StructPollfd p=new StructPollfd(); p.fd=socket.getFileDescriptor();
            p.events=(short)(write?OsConstants.POLLOUT:OsConstants.POLLIN);
            try {
                if(Os.poll(new StructPollfd[]{p},(int)Math.min(left,100))==0) continue;
                if((p.revents&p.events)==0) throw new EOFException("Canal PCM fermé");
                int n=write?Os.write(p.fd,b,offset,length-offset):Os.read(p.fd,b,offset,length-offset);
                if(n<=0) throw new EOFException("Trame PCM incomplète");
                offset+=n;
            } catch(ErrnoException e) { if(e.errno!=OsConstants.EINTR && e.errno!=OsConstants.EAGAIN) throw e; }
        }
    }
    // Each direction has exactly one owner thread; a frame has one total deadline.
    void write(byte[] pcm,int length,long timeout) throws Exception {
        if(length>0 && (pcm==null || pcm.length<length)) throw new IOException("Buffer trop court");
        long end=SystemClock.elapsedRealtime()+timeout;
        transfer(PcmProtocol.header(length),4,true,end);
        if(length>0) transfer(pcm,length,true,end);
    }
    byte[] read(long timeout) throws Exception {
        long end=SystemClock.elapsedRealtime()+timeout;
        byte[] header=new byte[4]; transfer(header,4,false,end);
        int n=PcmProtocol.length(header);
        if(!PcmProtocol.valid(n)) throw new IOException("Trame PCM invalide");
        if(n==PcmProtocol.STOP) throw new EOFException("Arrêt PCM demandé");
        if(n==PcmProtocol.HEARTBEAT) return new byte[0];
        byte[] pcm=new byte[n];
        try { transfer(pcm,n,false,end); return pcm; }
        catch(Exception e) { Arrays.fill(pcm,(byte)0); throw e; }
    }
    @Override public void close() {
        if(!closed.compareAndSet(false,true)) return;
        try { Os.shutdown(socket.getFileDescriptor(),OsConstants.SHUT_RDWR); } catch(Exception ignored) {}
        try { socket.close(); } catch(Exception ignored) {}
    }
}
