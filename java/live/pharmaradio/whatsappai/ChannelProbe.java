package live.pharmaradio.whatsappai;

import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.system.ErrnoException;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

/** Uses only poll/read/write on the transferred descriptor; never changes socket options. */
final class ChannelProbe {
    static void verify(FileDescriptor fd) throws Exception {
        byte[] expected = ByteBuffer.allocate(8).putLong(new java.security.SecureRandom().nextLong()).array();
        byte[] actual = new byte[8];
        long deadline = SystemClock.elapsedRealtime() + 3000;
        transfer(fd, expected, true, deadline);
        transfer(fd, actual, false, deadline);
        if (!java.util.Arrays.equals(expected, actual)) throw new IOException("Réponse du canal incorrecte");
    }

    private static void transfer(FileDescriptor fd, byte[] bytes, boolean writing, long deadline) throws Exception {
        int offset = 0;
        while (offset < bytes.length) {
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new SocketTimeoutException("Délai du canal dépassé");
            StructPollfd poll = new StructPollfd();
            poll.fd = fd;
            poll.events = (short) (writing ? OsConstants.POLLOUT : OsConstants.POLLIN);
            try {
                if (Os.poll(new StructPollfd[]{poll}, (int) remaining) == 0) continue;
                if ((poll.revents & poll.events) == 0) throw new IOException("Canal fermé ou indisponible");
                // The socket has a single reader/writer; at most eight bytes are transferred.
                int count = writing ? Os.write(fd, bytes, offset, bytes.length - offset)
                        : Os.read(fd, bytes, offset, bytes.length - offset);
                if (count <= 0) throw new IOException("Canal fermé avant la réponse complète");
                offset += count;
            } catch (ErrnoException error) {
                if (error.errno != OsConstants.EINTR) throw error;
            }
        }
    }
}
