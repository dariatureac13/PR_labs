package lab1;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Task1a {

    public static class Result {
        long negatives = 0;
        long positives = 0;
        long zeros = 0;

        public void merge(Result other) {
            this.negatives += other.negatives;
            this.positives += other.positives;
            this.zeros += other.zeros;
        }
    }

    private static class ChunkWorker implements Callable<Result> {
        private final File file;
        private final long startByte;
        private final long endByte;

        public ChunkWorker(File file, long startByte, long endByte) {
            this.file = file;
            this.startByte = startByte;
            this.endByte = endByte;
        }

        @Override
        public Result call() throws Exception {
            Result result = new Result();
            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 FileChannel channel = raf.getChannel()) {

                long start = startByte;
                if (start > 0) {
                    channel.position(start - 1);
                    ByteBuffer oneByte = ByteBuffer.allocate(1);
                    channel.read(oneByte);
                    if (oneByte.get(0) != '\n') {
                        ByteBuffer buf = ByteBuffer.allocate(1024);
                        while (channel.read(buf) != -1) {
                            buf.flip();
                            while (buf.hasRemaining()) {
                                start++;
                                if (buf.get() == '\n') break;
                            }
                            if (buf.get(buf.position() - 1) == '\n') break;
                            buf.clear();
                        }
                    }
                }

                channel.position(start);
                long remainingBytes = endByte - start;
                if (remainingBytes <= 0) return result;

                ByteBuffer buffer = ByteBuffer.allocate(65536);
                StringBuilder sb = new StringBuilder();
                long bytesReadTotal = 0;

                while (bytesReadTotal < remainingBytes) {
                    buffer.clear();
                    int limit = (int) Math.min(buffer.capacity(), remainingBytes - bytesReadTotal);
                    buffer.limit(limit);
                    int read = channel.read(buffer);
                    if (read == -1) break;
                    bytesReadTotal += read;
                    buffer.flip();

                    while (buffer.hasRemaining()) {
                        byte b = buffer.get();
                        if (b == '\n' || b == '\r') {
                            if (sb.length() > 0) {
                                parseAndCount(sb.toString(), result);
                                sb.setLength(0);
                            }
                        } else {
                            sb.append((char) b);
                        }
                    }
                }
                if (sb.length() > 0) {
                    parseAndCount(sb.toString(), result);
                }
            }
            return result;
        }

        private void parseAndCount(String line, Result res) {
            try {
                int num = Integer.parseInt(line.trim());
                if (num < 0) res.negatives++;
                else if (num > 0) res.positives++;
                else res.zeros++;
            } catch (NumberFormatException ignored) {}
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
            long end = (i == numThreads - 1) ? fileSize : (i + 1) * chunkSize;
            futures.add(executor.submit(new ChunkWorker(file, start, end)));
        }

        executor.shutdown();

        Result totalResult = new Result();
        for (Future<Result> future : futures) {
            totalResult.merge(future.get());
        }

        return totalResult;
    }
}