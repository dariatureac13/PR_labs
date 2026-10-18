package lab1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Task 1c: one reader + N workers + a blocking queue with a capacity of 100 batches of 10,000 rows each. */
public class Task1c {
    private static final int BATCH = 10_000;
    private static final int QUEUE = 100;
    private static final List<String> POISON = new ArrayList<>(); // метка "конец файла"

    public static NumberCounter run(int workers, String path) throws Exception {
        ArrayBlockingQueue<List<String>> queue = new ArrayBlockingQueue<>(QUEUE);
        NumberCounter total = new NumberCounter();
        ExecutorService ex = Executors.newFixedThreadPool(workers + 1);
        List<Future<?>> fs = new ArrayList<>();

        // Reader
        fs.add(ex.submit(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader(path), 1 << 16)) {
                List<String> batch = new ArrayList<>(BATCH);
                String line;
                while ((line = br.readLine()) != null) {
                    batch.add(line);
                    if (batch.size() == BATCH) {
                        queue.put(batch);          // waits, if queue is full
                        batch = new ArrayList<>(BATCH);
                    }
                }
                if (!batch.isEmpty()) queue.put(batch);
            } finally {
                // even if the reader has failed, the workers must receive a stop signal
                for (int i = 0; i < workers; i++) queue.put(POISON);
            }
            return null;
        }));

        // Workers: perform parsing OUTSIDE the lock; only 3 additions occur under the lock, right at the very end.
        for (int w = 0; w < workers; w++) {
            fs.add(ex.submit(() -> {
                long n = 0, p = 0, z = 0;
                while (true) {
                    List<String> batch = queue.take(); // waits, if queue is empty
                    if (batch == POISON) break;
                    for (String s : batch) {
                        int v = Integer.parseInt(s.trim());
                        if (v < 0) n++;
                        else if (v > 0) p++;
                        else z++;
                    }
                }
                total.mergeCounts(n, p, z);
                return null;
            }));
        }
        ex.shutdown();
        for (Future<?> f : fs) f.get(); // will throw an exception if something fails
        return total;
    }
}