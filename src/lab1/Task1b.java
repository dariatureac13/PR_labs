package lab1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Task1b {

    private static int[] numbers;

    // Step 1: loading all numbers into memory once
    public static void loadData() throws Exception {
        if (numbers != null) return;
        numbers = new int[50_000_000];
        try (BufferedReader br = new BufferedReader(new FileReader("numbers.txt"))) {
            String line;
            int idx = 0;
            while ((line = br.readLine()) != null && idx < 50_000_000) {
                numbers[idx++] = Integer.parseInt(line.trim());
            }
        }
    }

    public static void runAll(int numThreads) throws Exception {
        loadData();

        System.out.println("--- Starting Task 1b with " + numThreads + " threads ---");

        // Variant 1: Without synchronization (race condition)
        long start = System.currentTimeMillis();
        UnsyncResult uRes = runUnsynchronized(numThreads);
        System.out.println("[1b] Unsynchronized -> Time: " + (System.currentTimeMillis() - start) + 
                " ms | Zeros: " + uRes.zeros + " (Note: totals might be incorrect due to race condition)");

        // Variant 2: Locked
        start = System.currentTimeMillis();
        SyncResult sRes = runLocked(numThreads);
        System.out.println("[1b] Locked (ReentrantLock) -> Time: " + (System.currentTimeMillis() - start) + 
                " ms | Zeros: " + sRes.zeros);

        // Variant 3: Thread-local
        start = System.currentTimeMillis();
        LocalResult lRes = runLocal(numThreads);
        System.out.println("[1b] Thread-Local Counters -> Time: " + (System.currentTimeMillis() - start) + 
                " ms | Zeros: " + lRes.zeros);
    }

    //  Way 1: No synchronization
    private static class UnsyncResult { long neg = 0, pos = 0, zeros = 0; }
    
    private static UnsyncResult runUnsynchronized(int threads) throws Exception {
        UnsyncResult res = new UnsyncResult();
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        int chunkSize = numbers.length / threads;
        
        for (int i = 0; i < threads; i++) {
            final int start = i * chunkSize;
            final int end = (i == threads - 1) ? numbers.length : (start + chunkSize);
            ex.submit(() -> {
                for (int j = start; j < end; j++) {
                    if (numbers[j] < 0) res.neg++;
                    else if (numbers[j] > 0) res.pos++;
                    else res.zeros++;
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(1, TimeUnit.MINUTES);
        return res;
    }

    // Way 2: ReentrantLock
    private static class SyncResult { long neg = 0, pos = 0, zeros = 0; }
    
    private static SyncResult runLocked(int threads) throws Exception {
        SyncResult res = new SyncResult();
        Lock lock = new ReentrantLock();
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        int chunkSize = numbers.length / threads;
        
        for (int i = 0; i < threads; i++) {
            final int start = i * chunkSize;
            final int end = (i == threads - 1) ? numbers.length : (start + chunkSize);
            ex.submit(() -> {
                long lNeg = 0, lPos = 0, lZero = 0;
                for (int j = start; j < end; j++) {
                    if (numbers[j] < 0) lNeg++;
                    else if (numbers[j] > 0) lPos++;
                    else lZero++;
                }
                lock.lock();
                try {
                    res.neg += lNeg; 
                    res.pos += lPos; 
                    res.zeros += lZero;
                } finally {
                    lock.unlock();
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(1, TimeUnit.MINUTES);
        return res;
    }

    // Way 3: Local counters ( fastest and safest)
    private static class LocalResult { long neg = 0, pos = 0, zeros = 0; }
    
    private static LocalResult runLocal(int threads) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        LocalResult[] results = new LocalResult[threads];
        int chunkSize = numbers.length / threads;
        
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            final int start = i * chunkSize;
            final int end = (i == threads - 1) ? numbers.length : (start + chunkSize);
            results[idx] = new LocalResult();
            
            ex.submit(() -> {
                for (int j = start; j < end; j++) {
                    if (numbers[j] < 0) results[idx].neg++;
                    else if (numbers[j] > 0) results[idx].pos++;
                    else results[idx].zeros++;
                }
            });
        }
        ex.shutdown();
        ex.awaitTermination(1, TimeUnit.MINUTES);
        
        LocalResult total = new LocalResult();
        for (LocalResult r : results) {
            total.neg += r.neg; 
            total.pos += r.pos; 
            total.zeros += r.zeros;
        }
        return total;
    }
}