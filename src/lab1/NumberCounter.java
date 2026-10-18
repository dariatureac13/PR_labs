package lab1;

/**
 * A shared parser for Task 1a and Task 2 (the same code is used for both, ensuring a fair comparison).
 * It reads bytes one by one, assembles the number, and determines its sign upon encountering a newline character ('\n').
 * It does not create any `String` instances or other objects.
 */
public final class NumberCounter {
    public long negatives, positives, zeros;

    private int num;
    private boolean negative, inNumber;

    public void add(byte b) {
        if (b >= '0' && b <= '9') {
            num = num * 10 + (b - '0');
            inNumber = true;
        } else if (b == '-') {
            negative = true;
        } else if (b == '\n') {
            endLine();
        }
        // '\r' и прочее игнорируем
    }

    /** Finish the current line (must be called at the end if the file does not end with '\n'). */
    public void endLine() {
        if (inNumber) {
            int v = negative ? -num : num;
            if (v < 0) negatives++;
            else if (v > 0) positives++;
            else zeros++;
        }
        num = 0;
        negative = false;
        inNumber = false;
    }

    public void merge(NumberCounter o) {
        negatives += o.negatives;
        positives += o.positives;
        zeros += o.zeros;
    }

    /** For workers from Task 1c: add three numbers under the lock, once per worker. */
    public synchronized void mergeCounts(long n, long p, long z) {
        negatives += n;
        positives += p;
        zeros += z;
    }

    public long total() {
        return negatives + positives + zeros;
    }
}