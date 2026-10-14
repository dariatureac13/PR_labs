package lab1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Task1c {

    // Special thread termination marker (Poison Pill)
    private static final List<String> POISON_PILL = new ArrayList<>();

    public static class Result {
        public long negatives = 0;
        public long positives = 0;
        public long zeros = 0;

        public synchronized void merge(List<String> batch) {
            for (String s : batch) {
                try {
                    int val = Integer.parseInt(s.trim());
                    if (val < 0) negatives++;
                    else if (val > 0) positives++;
                    else zeros++;
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    public static Result runTask(int numWorkers) throws Exception {
        // A queue with a buffer size limit of 100 batches
        ArrayBlockingQueue<List<String>> queue = new ArrayBlockingQueue<>(100);
        Result totalResult = new Result();

        // Thread pool: 1 reading thread (Producer) + numWorkers for computation (Consumers).
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers + 1);

        // PRODUCER (Reading Thread)
        executor.submit(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader("numbers.txt"))) {
                List<String> batch = new ArrayList<>(10_000);
                String line;
                
                while ((line = br.readLine()) != null) {
                    batch.add(line);
                    if (batch.size() == 10_000) {
                        queue.put(batch); // Blocks the thread if the queue is full.
                        batch = new ArrayList<>(10_000);
                    }
                }
                if (!batch.isEmpty()) {
                    queue.put(batch);
                }
                
                // Sending "poison pills" to each worker so they know the file has ended.
                for (int i = 0; i < numWorkers; i++) {
                    queue.put(POISON_PILL);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        //  CONSUMERS (Working threads)
        for (int i = 0; i < numWorkers; i++) {
            executor.submit(() -> {
                try {
                    while (true) {
                        List<String> batch = queue.take(); // Waits until a batch appears in the queue.
                        if (batch == POISON_PILL) {
                            break; // Exiting the loop upon receiving the termination signal.
                        }
                        totalResult.merge(batch);
                    }
                } catch (Exception ignored) {}
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);
        return totalResult;
    }
}