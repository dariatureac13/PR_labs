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
            System.gc();
            long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long startTime = System.currentTimeMillis();

            if (task.equals("1a")) {
                Task1a.Result res = Task1a.runTask(threads);
                System.out.println("Negatives: " + res.negatives);
                System.out.println("Positives: " + res.positives);
                System.out.println("Zeros: " + res.zeros);
                System.out.println("Total sum check: " + (res.negatives + res.positives + res.zeros));
            }

            long endTime = System.currentTimeMillis();
            long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            System.out.println("Execution Time: " + (endTime - startTime) + " ms");
            System.out.println("Peak Memory: " + Math.max(0, (memoryAfter - memoryBefore) / (1024 * 1024)) + " MB");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}