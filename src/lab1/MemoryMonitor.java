package lab1;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** Measures peak memory usage: heap peak (via sampling) and process RSS peak.*/
public final class MemoryMonitor extends Thread {
    private volatile boolean running = true;
    private volatile long peakHeap = 0;

    private MemoryMonitor() {
        setDaemon(true);
    }

    public static MemoryMonitor begin() {
        MemoryMonitor m = new MemoryMonitor();
        m.start();
        return m;
    }

    @Override
    public void run() {
        Runtime rt = Runtime.getRuntime();
        while (running) {
            long used = rt.totalMemory() - rt.freeMemory();
            if (used > peakHeap) peakHeap = used;
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public void end() {
        running = false;
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        if (used > peakHeap) peakHeap = used;
    }

    public long peakHeapMB() {
        return peakHeap / (1024 * 1024);
    }

    /** Peak resident memory usage of a process in MB (Linux only), otherwise -1. */
    public static long peakRssMB() {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/self/status"));
            for (String l : lines) {
                if (l.startsWith("VmHWM:")) {
                    String[] p = l.trim().split("\\s+");
                    return Long.parseLong(p[1]) / 1024; // кБ -> МБ
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }
}