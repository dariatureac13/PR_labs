package coffeeshop;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * One order. Its state changes only inside the monitor of the order, so a
 * worker that finishes and a client thread that cancels cannot both win.
 */
final class Order {
    enum State { QUEUED, PREPARING, READY, CANCELLED }

    final int id;
    final Connection owner;
    final Map<Product, Integer> items;
    final long prepSeconds;
    private State state = State.QUEUED;                  // guarded by "this"

    Order(int id, Connection owner, Map<Product, Integer> items, long prepSeconds) {
        this.id = id;
        this.owner = owner;
        this.items = new EnumMap<>(items);
        this.prepSeconds = prepSeconds;
    }

    String describe() {
        StringBuilder sb = new StringBuilder();
        for (var e : items.entrySet()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(e.getValue()).append('x').append(e.getKey().label());
        }
        return sb.toString();
    }

    /** Called by a worker that took the order from the queue. False: it was cancelled while waiting. */
    synchronized boolean startPreparing() {
        if (state != State.QUEUED) return false;
        state = State.PREPARING;
        return true;
    }

    /**
     * Simulated preparation = waiting. The wait ends early when cancel() calls notifyAll().
     * Returns true if the order is READY, false if it was cancelled.
     */
    synchronized boolean prepare() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(prepSeconds);
        while (state == State.PREPARING) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                state = State.READY;
                return true;
            }
            TimeUnit.NANOSECONDS.timedWait(this, left);
        }
        return false;
    }

    /** Returns the previous state, or null when nothing can be cancelled (already READY or CANCELLED). */
    synchronized State cancel() {
        State prev = state;
        if (prev == State.READY || prev == State.CANCELLED) return null;
        state = State.CANCELLED;
        notifyAll();                                      // wakes the worker inside prepare()
        return prev;
    }
}