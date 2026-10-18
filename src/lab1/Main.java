package lab1;

public class Main {
    public static final long EXPECTED = Long.getLong("expected", 50_000_000L);

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java lab1.Main <1a|1b|1c|2> <threads> [dir-for-task2]");
            System.out.println("  1b ignores <threads>: it uses all logical processors.");
            return;
        }
        String task = args[0];
        int threads = Integer.parseInt(args[1]);
        String dir = args.length > 2 ? args[2] : ".";

        System.gc();
        Thread.sleep(100);
        MemoryMonitor mon = MemoryMonitor.begin();
        long t0 = System.nanoTime();
        NumberCounter res = null;

        switch (task) {
            case "1a" -> res = Task1a.run(threads, "numbers.txt");
            case "1b" -> Task1b.runAll("numbers.txt");
            case "1c" -> res = Task1c.run(threads, "numbers.txt");
            case "2" -> res = Task2.run(threads, dir);
            default -> {
                System.out.println("Unknown task: " + task);
                return;
            }
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        mon.end();

        if (res != null) {
            System.out.println("Negatives: " + res.negatives);
            System.out.println("Positives: " + res.positives);
            System.out.println("Zeros:     " + res.zeros);
            System.out.println("Total:     " + res.total() + (res.total() == EXPECTED ? "  OK" : "  MISMATCH (expected " + EXPECTED + ")"));
            System.out.println("Execution Time: " + ms + " ms");
        } else {
            System.out.println("(1b: time of each version is printed above; total time here includes loading)");
        }
        System.out.println("Peak heap (sampled): " + mon.peakHeapMB() + " MB");
        long rss = MemoryMonitor.peakRssMB();
        System.out.println("Peak RSS (VmHWM):    " + (rss < 0 ? "n/a on this OS, use /usr/bin/time -l or Task Manager" : rss + " MB"));
    }
}