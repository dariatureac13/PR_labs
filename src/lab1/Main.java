package lab1;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java lab1.Main <task> <threads>");
            return;
        }

        String task = args[0];
        int threads = Integer.parseInt(args[1]);

        try {
            // Force garbage collection before measurement to clear old objects
            System.gc();
            Thread.sleep(100); // Give the garbage collector thread time to run

            Runtime runtime = Runtime.getRuntime();
            long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
            long startTime = System.currentTimeMillis();

            // Execute tasks
            if (task.equals("1a")) {
                Task1a.Result res = Task1a.runTask(threads);
                printResult(res.negatives, res.positives, res.zeros);
            } else if (task.equals("1b")) {
                Task1b.runAll(threads);
            } else if (task.equals("1c")) {
                Task1c.Result res = Task1c.runTask(threads);
                printResult(res.negatives, res.positives, res.zeros);
            } else if (task.equals("2")) {
                Task2.Result res = Task2.runTask(threads);
                printResult(res.negatives, res.positives, res.zeros);
            }


            long endTime = System.currentTimeMillis();

            // Measure memory after execution
            long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
            long peakMemoryMB = Math.max(0, (memoryAfter - memoryBefore) / (1024 * 1024));

            System.out.println("Execution Time: " + (endTime - startTime) + " ms");
            System.out.println("Peak Memory (approx): " + peakMemoryMB + " MB");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printResult(long neg, long pos, long zeros) {
        System.out.println("Negatives: " + neg);
        System.out.println("Positives: " + pos);
        System.out.println("Zeros: " + zeros);
        System.out.println("Total sum check: " + (neg + pos + zeros));
    }
}