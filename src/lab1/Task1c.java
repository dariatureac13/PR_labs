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

    // Специальный маркер окончания работы (ядрышко яда / Poison Pill) для потоков
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
        // Очередь с ограничением размера буфера в 100 батчей
        ArrayBlockingQueue<List<String>> queue = new ArrayBlockingQueue<>(100);
        Result totalResult = new Result();

        // Пулин потоков: 1 поток для чтения (Producer) + numWorkers для счета (Consumers)
        ExecutorService executor = Executors.newFixedThreadPool(numWorkers + 1);

        // --- PRODUCER (Поток-читатель) ---
        executor.submit(() -> {
            try (BufferedReader br = new BufferedReader(new FileReader("numbers.txt"))) {
                List<String> batch = new ArrayList<>(10_000);
                String line;
                
                while ((line = br.readLine()) != null) {
                    batch.add(line);
                    if (batch.size() == 10_000) {
                        queue.put(batch); // Блокирует поток, если очередь заполнена
                        batch = new ArrayList<>(10_000);
                    }
                }
                if (!batch.isEmpty()) {
                    queue.put(batch);
                }
                
                // Рассылаем "таблетки смерти" для каждого воркера, чтобы они знали, что файл кончился
                for (int i = 0; i < numWorkers; i++) {
                    queue.put(POISON_PILL);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // --- CONSUMERS (Потоки-воркеры) ---
        for (int i = 0; i < numWorkers; i++) {
            executor.submit(() -> {
                try {
                    while (true) {
                        List<String> batch = queue.take(); // Ждет, пока появится батч в очереди
                        if (batch == POISON_PILL) {
                            break; // Выходим из цикла при получении сигнала завершения
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