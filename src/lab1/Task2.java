package lab1;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Task2 {

    public static class Result {
        public long negatives = 0;
        public long positives = 0;
        public long zeros = 0;

        public void merge(Result other) {
            this.negatives += other.negatives;
            this.positives += other.positives;
            this.zeros += other.zeros;
        }
    }

    private static class MappedWorker implements Callable<Result> {
        private final File file;
        private final long startByte;
        private final long size;

        public MappedWorker(File file, long startByte, long size) {
            this.file = file;
            this.startByte = startByte;
            this.size = size;
        }

        @Override
        public Result call() throws Exception {
            Result result = new Result();
            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 FileChannel channel = raf.getChannel()) {

                MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, startByte, size);

                long startPos = 0;
                // Shift to the beginning of the first full line if this is not the first chunk
                if (startByte > 0) {
                    while (startPos < size && buffer.get((int) startPos) != '\n') {
                        startPos++;
                    }
                    startPos++; // Skip the '\n' itself
                }

                int num = 0;
                boolean isNegative = false;
                boolean inNumber = false;

                for (long i = startPos; i < size; i++) {
                    byte b = buffer.get((int) i);

                    if (b >= '0' && b <= '9') {
                        num = num * 10 + (b - '0');
                        inNumber = true;
                    } else if (b == '-') {
                        isNegative = true;
                    } else if (b == '\n' || b == '\r') {
                        if (inNumber) {
                            countNumber(isNegative ? -num : num, result);
                            num = 0;
                            isNegative = false;
                            inNumber = false;
                        }
                    }
                }

                // Read the last number of the chunk beyond its boundary up to the nearest newline character
                if (startByte + size < file.length()) {
                    long extraPos = startByte + size;
                    raf.seek(extraPos);
                    int b;
                    while ((b = raf.read()) != -1) {
                        if (b >= '0' && b <= '9') {
                            num = num * 10 + (b - '0');
                            inNumber = true;
                        } else if (b == '-') {
                            isNegative = true;
                        } else if (b == '\n' || b == '\r') {
                            if (inNumber) {
                                countNumber(isNegative ? -num : num, result);
                            }
                            break;
                        }
                    }
                } else if (inNumber) {
                    countNumber(isNegative ? -num : num, result);
                }
            }
            return result;
        }

        private void countNumber(int val, Result res) {
            if (val < 0) res.negatives++;
            else if (val > 0) res.positives++;
            else res.zeros++;
        }
    }

    public static Result runTask(int numThreads) throws Exception {
        File file = new File("numbers.txt");
        long fileSize = file.length();
        long chunkSize = fileSize / numThreads;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<Result>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            long start = i * chunkSize;
            long size = (i == numThreads - 1) ? (fileSize - start) : chunkSize;
            futures.add(executor.submit(new MappedWorker(file, start, size)));
        }

        executor.shutdown();

        Result totalResult = new Result();
        for (Future<Result> future : futures) {
            totalResult.merge(future.get());
        }

        return totalResult;
    }
}