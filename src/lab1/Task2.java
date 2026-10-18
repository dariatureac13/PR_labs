package lab1;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class Task2 {
    public static final int FILES = 8;

    public static NumberCounter run(int threads, String dir) throws Exception {
        File[] files = new File[FILES];
        for (int i = 0; i < FILES; i++) files[i] = new File(dir, "numbers_" + (i + 1) + ".txt");

        ExecutorService ex = Executors.newFixedThreadPool(threads);
        List<Future<NumberCounter>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            fs.add(ex.submit(() -> {
                NumberCounter c = new NumberCounter();
                for (int i = tid; i < FILES; i += threads) {
                    byte[] data = Files.readAllBytes(files[i].toPath()); // a single read operation
                    for (byte b : data) c.add(b);                        // the same parser as in 1a
                    c.endLine();                                         // the file might have ended without a '\n'
                }
                return c;
            }));
        }
        ex.shutdown();
        NumberCounter total = new NumberCounter();
        for (Future<NumberCounter> f : fs) total.merge(f.get());
        return total;
    }
}