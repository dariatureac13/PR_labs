package lab1;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java -jar lab1.jar <task> <threads>");
            System.out.println("Example: java -jar lab1.jar 1a 8");
            return;
        }

        String task = args[0];
        int threads = Integer.parseInt(args[1]);

        // Clean up memory before execution for accurate measurement
        System.gc();
        long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long startTime = System.currentTimeMillis();

        System.out.println("Running Task " + task + " with " + threads + " thread(s)...");

        // TODO: Call specific task logic based on CLI arguments

        long endTime = System.currentTimeMillis();
        long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long executionTime = endTime - startTime;
        long peakMemoryMb = Math.max(0, (memoryAfter - memoryBefore) / (1024 * 1024));

        System.out.println("Execution Time: " + executionTime + " ms");
        System.out.println("Peak Memory Usage: " + peakMemoryMb + " MB");
    }
}