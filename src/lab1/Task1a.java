package lab1;

import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Task 1a. A file is divided into N byte ranges [start, end).
 * Boundary rule: a thread processes exactly those lines that BEGIN within [start, end).
 *  - If start > 0: examine the data starting from byte `start-1` and skip everything up to and including the first '\n' encountered
 *  (this partial line belongs to the preceding thread);
 *  Any line that begins before `end` must be read in its entirety, even if it extends beyond the `end` boundary.
 */
public class Task1a {

    public static NumberCounter run(int threads, String path) throws Exception {
        long size;
        try (RandomAccessFile raf = new RandomAccessFile(path, "r")) {
            size = raf.length();
        }
        long chunk = size / threads;
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<NumberCounter>> fs = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            long start = i * chunk;
            long end = (i == threads - 1) ? size : (i + 1) * chunk;
            fs.add(ex.submit(() -> processRange(path, start, end)));
        }
        ex.shutdown();
        NumberCounter total = new NumberCounter();
        for (Future<NumberCounter> f : fs) total.merge(f.get());
        return total;
    }

    private static NumberCounter processRange(String path, long start, long end) throws Exception {
        NumberCounter c = new NumberCounter();
        try (RandomAccessFile raf = new RandomAccessFile(path, "r");
             FileChannel ch = raf.getChannel()) {

            boolean skipping = start > 0;
            long pos = skipping ? start - 1 : start;
            ch.position(pos);

            byte[] arr = new byte[1 << 16];
            ByteBuffer bb = ByteBuffer.wrap(arr);
            boolean done = false;
            while (!done) {
                bb.clear();
                int n = ch.read(bb);
                if (n < 0) break;
                for (int i = 0; i < n; i++) {
                    byte b = arr[i];
                    pos++;
                    if (skipping) {
                        if (b == '\n') {
                            skipping = false;
                            if (pos >= end) { done = true; break; }
                        }
                        continue;
                    }
                    c.add(b);
                    if (b == '\n' && pos >= end) { done = true; break; }
                }
            }
            if (!skipping) c.endLine();
        }
        return c;
    }
}