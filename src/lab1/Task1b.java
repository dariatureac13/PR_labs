package lab1;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

/** Task 1b: Three ways to count signs in an `int[]` (loading time is not measured). */
public class Task1b {
    private static int[] numbers;
    private static int count;

    private interface Range {
        void run(int idx, int start, int end);
    }

    private static class Shared {
        long neg, pos, zeros; // ordinary fields, without volatile and without locks
    }

    public static void runAll(String path) throws Exception {
        load(path);
        int threads = Runtime.getRuntime().availableProcessors();
        System.out.println("Logical processors = threads = " + threads + ", numbers = " + count);

        // JIT warm-up (not measured), so that the first version doesn't incur the cost of compilation
        countLocal(threads);

        // Version 1: without synchronization, 3 runs
        for (int run = 1; run <= 3; run++) {
            long t0 = System.nanoTime();
            long[] r = countUnsync(threads);
            report("1) unsynchronized, run " + run, t0, r);
        }
        // Version 2: shared counters under lock (a lock on EACH number)
        long t0 = System.nanoTime();
        long[] r2 = countLocked(threads);
        report("2) shared counters + lock", t0, r2);
        // Version 3: counter for a stream
        t0 = System.nanoTime();
        long[] r3 = countLocal(threads);
        report("3) per-thread counters   ", t0, r3);
    }

    private static void report(String name, long t0, long[] r) {
        long ms = (System.nanoTime() - t0) / 1_000_000;
        long total = r[0] + r[1] + r[2];
        System.out.printf("[1b] %-30s %6d ms | neg=%d pos=%d zero=%d | total=%d %s%n",
                name, ms, r[0], r[1], r[2], total, total == Main.EXPECTED ? "OK" : "MISMATCH");
    }

    private static void load(String path) throws Exception {
        numbers = new int[(int) Main.EXPECTED];
        try (BufferedReader br = new BufferedReader(new FileReader(path), 1 << 16)) {
            String line;
            while ((line = br.readLine()) != null) numbers[count++] = Integer.parseInt(line.trim());
        }
    }

    private static void parallel(int threads, Range body) throws Exception {
        ExecutorService ex = Executors.newFixedThreadPool(threads);
        int chunk = count / threads;
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            final int s = i * chunk;
            final int e = (i == threads - 1) ? count : s + chunk;
            tasks.add(() -> { body.run(idx, s, e); return null; });
        }
        for (Future<Void> f : ex.invokeAll(tasks)) f.get();
        ex.shutdown();
    }

    // Version 1: all threads perform an increment (++) operation on the exact same fields. This is a data race.
    private static long[] countUnsync(int threads) throws Exception {
        Shared sh = new Shared();
        parallel(threads, (idx, s, e) -> {
            for (int j = s; j < e; j++) {
                int v = numbers[j];
                if (v < 0) sh.neg++;
                else if (v > 0) sh.pos++;
                else sh.zeros++;
            }
        });
        return new long[]{sh.neg, sh.pos, sh.zeros};
    }

    // Version 2: same shared fields, but each `++` operation is wrapped in a `lock`/`unlock`. Correct, but slow.
    private static long[] countLocked(int threads) throws Exception {
        Shared sh = new Shared();
        ReentrantLock lock = new ReentrantLock();
        parallel(threads, (idx, s, e) -> {
            for (int j = s; j < e; j++) {
                int v = numbers[j];
                lock.lock();
                try {
                    if (v < 0) sh.neg++;
                    else if (v > 0) sh.pos++;
                    else sh.zeros++;
                } finally {
                    lock.unlock();
                }
            }
        });
        return new long[]{sh.neg, sh.pos, sh.zeros};
    }

    // Version 3: each thread has its own local variables; we sum them up after the join.
    private static long[] countLocal(int threads) throws Exception {
        long[][] parts = new long[threads][];
        parallel(threads, (idx, s, e) -> {
            long n = 0, p = 0, z = 0;
            for (int j = s; j < e; j++) {
                int v = numbers[j];
                if (v < 0) n++;
                else if (v > 0) p++;
                else z++;
            }
            parts[idx] = new long[]{n, p, z}; //  record it once at the end
        });
        long[] t = new long[3];
        for (long[] p : parts) { t[0] += p[0]; t[1] += p[1]; t[2] += p[2]; }
        return t;
    }
}