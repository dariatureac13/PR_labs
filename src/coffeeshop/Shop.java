package coffeeshop;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The business logic: stock, the FIFO queue of orders, the workers, the provider.
 * Producer-consumer: client threads PRODUCE orders into "queue", workers CONSUME them.
 */
final class Shop {
    static final int MAX_QTY = 100_000;

    private final Stock stock = new Stock();
    private final BlockingQueue<Order> queue = new LinkedBlockingQueue<>();   // FIFO

    private final Object orderLock = new Object();    // ids and queue order stay consistent
    private int lastOrderId = 0;                      // guarded by orderLock

    private final Object providerLock = new Object();
    private Connection provider;                      // guarded by providerLock

    Shop() {
        int workers = Integer.getInteger("shop.workers", Runtime.getRuntime().availableProcessors());
        Log.info("initial stock: " + fmt(stock.snapshot()));
        Log.info("starting " + workers + " worker(s)");
        for (int i = 1; i <= workers; i++) {
            Thread t = new Thread(this::workerLoop, "worker-" + i);
            t.setDaemon(true);
            t.start();
        }
    }

    // ---------------------------------------------------------------- parsing helpers

    /** Parses "<product>=<qty> ..." (tokens 1..n). Throws IllegalArgumentException with a message for the user. */
    static Map<Product, Integer> parseItems(String[] parts) {
        if (parts.length < 2) {
            throw new IllegalArgumentException("missing parameters, expected: " + parts[0].toUpperCase() + " <product>=<quantity> ...");
        }
        EnumMap<Product, Integer> items = new EnumMap<>(Product.class);
        for (int i = 1; i < parts.length; i++) {
            String[] kv = parts[i].split("=", -1);
            if (kv.length != 2) {
                throw new IllegalArgumentException("bad item '" + safe(parts[i]) + "', expected <product>=<quantity>");
            }
            Product p = Product.parse(kv[0]);
            if (p == null) {
                throw new IllegalArgumentException("unknown product '" + safe(kv[0]) + "' (coffee, tea, croissant)");
            }
            int q;
            try {
                q = Integer.parseInt(kv[1]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("quantity '" + safe(kv[1]) + "' is not a number");
            }
            if (q <= 0) throw new IllegalArgumentException("quantity must be a positive number");
            if (q > MAX_QTY) throw new IllegalArgumentException("quantity too big (max " + MAX_QTY + ")");
            items.merge(p, q, Integer::sum);
        }
        return items;
    }

    /** Makes user supplied text safe to echo and to log (no control characters, bounded length). */
    static String safe(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && i < 30; i++) {
            char c = s.charAt(i);
            sb.append(c < 32 || c == 127 || c == '\uFFFD' ? '?' : c);
        }
        if (s.length() > 30) sb.append("...");
        return sb.toString();
    }

    static String fmt(Map<Product, Integer> m) {
        StringBuilder sb = new StringBuilder();
        for (var e : m.entrySet()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(e.getKey().label()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- commands

    String menu() {
        Map<Product, Integer> s = stock.snapshot();
        StringBuilder sb = new StringBuilder("MENU");
        for (Product p : Product.values()) {
            sb.append(p.ordinal() == 0 ? " " : " | ")
                    .append(p.label()).append(": ").append(s.get(p)).append(" in stock, ").append(p.prepSeconds).append(" s");
        }
        return sb.toString();
    }

    void order(Connection c, Map<Product, Integer> items) {
        Stock.TakeResult r = stock.tryTake(items);        // atomic check + take
        if (!r.ok()) {
            List<String> parts = new ArrayList<>();
            for (var e : r.shortages().entrySet()) {
                parts.add(e.getKey().label() + ": requested " + items.get(e.getKey()) + ", left " + e.getValue());
            }
            String msg = String.join("; ", parts);
            c.send("REFUSED " + msg);
            Log.info(c.endpoint, "ORDER REFUSED (" + msg + ")");
            return;
        }
        long seconds = 0;
        for (var e : items.entrySet()) seconds += (long) e.getKey().prepSeconds * e.getValue();

        synchronized (orderLock) {
            Order o = new Order(++lastOrderId, c, items, seconds);
            c.addOrder(o);
            c.send("ACCEPTED order=" + o.id + " time=" + seconds + "s items=" + o.describe());   // answer at once
            Log.info(c.endpoint, "ORDER #" + o.id + " ACCEPTED (" + o.describe() + ", " + seconds + " s)");
            queue.add(o);                                  // after ACCEPTED: READY can never overtake it
        }
        if (!r.newlyLow().isEmpty()) alertProvider(r.newlyLow());
    }

    void deliver(Connection c, Map<Product, Integer> items) {
        stock.add(items);
        c.send("DELIVERED " + fmt(items));
        Log.info(c.endpoint, "DELIVERY " + fmt(items) + "; stock now " + fmt(stock.snapshot()));
    }

    // ---------------------------------------------------------------- provider

    /** Returns false if another provider is connected. Sends pending alerts at once. */
    boolean registerProvider(Connection c) {
        synchronized (providerLock) {
            if (provider != null) return false;
            provider = c;
        }
        Log.info(c.endpoint, "PROVIDER registered");
        c.send("OK role=PROVIDER");
        Map<Product, Integer> low = stock.lowProducts();   // alerts that were missed while it was away
        if (!low.isEmpty()) {
            c.send("ALERT low stock: " + fmt(low));
            Log.info(c.endpoint, "ALERT sent (pending): " + fmt(low));
        }
        return true;
    }

    private void alertProvider(Map<Product, Integer> low) {
        Connection p;
        synchronized (providerLock) {
            p = provider;
        }
        if (p == null) {
            Log.info("ALERT pending, provider not connected: " + fmt(low));
            return;
        }
        p.send("ALERT low stock: " + fmt(low));            // only puts it in the outbox; p's writer thread sends it
        Log.info(p.endpoint, "ALERT sent: " + fmt(low));
    }

    // ---------------------------------------------------------------- failures

    /** Called once when a connection is gone, for any reason. */
    void connectionClosed(Connection c) {
        synchronized (providerLock) {
            if (provider == c) {
                provider = null;
                Log.info(c.endpoint, "PROVIDER disconnected, the shop goes on");
            }
        }
        for (Order o : c.orders()) {
            Order.State prev = o.cancel();
            if (prev == null) continue;                    // already READY: delivered, nothing to undo
            queue.remove(o);                               // no-op if a worker already took it
            stock.add(o.items);                            // products go back; the prepared work is lost
            Log.info(c.endpoint, "ORDER #" + o.id + " CANCELLED (was " + prev + "), products returned: " + o.describe());
        }
    }

    // ---------------------------------------------------------------- workers

    private void workerLoop() {
        String name = Thread.currentThread().getName();
        while (true) {
            Order o;
            try {
                o = queue.take();                          // blocks while the queue is empty
            } catch (InterruptedException e) {
                return;
            }
            try {
                if (!o.startPreparing()) continue;         // cancelled while it was waiting
                Log.info(o.owner.endpoint, "ORDER #" + o.id + " PREPARING on " + name + " (" + o.prepSeconds + " s)");
                if (o.prepare()) {
                    o.owner.forget(o);
                    Log.info(o.owner.endpoint, "ORDER #" + o.id + " READY");
                    o.owner.send("READY order=" + o.id);
                } else {
                    Log.info(o.owner.endpoint, "ORDER #" + o.id + " preparation stopped on " + name);
                }
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                Log.error(o.owner.endpoint, "worker failed on order #" + o.id + ": " + t);
            }
        }
    }
}