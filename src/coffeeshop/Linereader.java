package coffeeshop;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Message framing. TCP is a byte stream: one send() is NOT one recv().
 * Our protocol is "one message = one line ended by '\n'". This class keeps
 * the bytes that were received but do not form a complete line yet, so
 *  - a message split in two pieces is glued together,
 *  - two messages in one read() are returned one after the other.
 * Lines longer than maxLen are reported once (LineTooLongException) and the
 * rest of such a line is skipped silently, so memory use stays bounded.
 */
final class LineReader {
    static final class LineTooLongException extends Exception {
        private static final long serialVersionUID = 1L;
        LineTooLongException(int max) {
            super("line longer than " + max + " bytes");
        }
    }

    private final InputStream in;
    private final int maxLen;
    private final byte[] buf = new byte[512];
    private int pos = 0, lim = 0;                       // unread bytes: buf[pos..lim)
    private final ByteArrayOutputStream line = new ByteArrayOutputStream();
    private boolean skipping = false;                    // inside a too long line

    LineReader(InputStream in, int maxLen) {
        this.in = in;
        this.maxLen = maxLen;
    }

    /** Blocks until a full line is available. Returns null when the peer closed the connection (read returned -1). */
    String readLine() throws IOException, LineTooLongException {
        while (true) {
            if (pos == lim) {
                int n = in.read(buf, 0, buf.length);    // blocks; may throw SocketTimeoutException / reset
                if (n < 0) return null;                  // FIN received: orderly close
                pos = 0;
                lim = n;
            }
            while (pos < lim) {
                byte b = buf[pos++];
                if (b == '\n') {
                    if (skipping) {                      // end of the too long line
                        skipping = false;
                        continue;
                    }
                    String s = line.toString(StandardCharsets.UTF_8);
                    line.reset();
                    return s.endsWith("\r") ? s.substring(0, s.length() - 1) : s;
                }
                if (skipping) continue;
                if (line.size() >= maxLen) {
                    skipping = true;
                    line.reset();
                    throw new LineTooLongException(maxLen);
                }
                line.write(b);
            }
        }
    }
}
