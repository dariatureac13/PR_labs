package coffeeshop;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The stock is shared by all client threads, the workers (returning products)
 * and the provider thread. EVERY access goes through a synchronized method,
 * and check-then-take is one atomic step (no "last croissant" race).
 */
final class Stock {
    static final int LOW = 5;

    private final EnumMap<Product, Integer> qty = new EnumMap<>(Product.class);
    private final EnumSet<Product> alerted = EnumSet.noneOf(Product.class);  // low and already reported

    Stock() {
        for (Product p : Product.values()) {
            qty.put(p, ThreadLocalRandom.current().nextInt(10, 51));
        }
    }

    /** shortages: product -> how many are left (empty when taken). newlyLow: products that just fell below LOW. */
    record TakeResult(Map<Product, Integer> shortages, Map<Product, Integer> newlyLow) {
        boolean ok() {
            return shortages.isEmpty();
        }
    }

    synchronized Map<Product, Integer> snapshot() {
        return new EnumMap<>(qty);
    }

    synchronized Map<Product, Integer> lowProducts() {
        EnumMap<Product, Integer> low = new EnumMap<>(Product.class);
        for (var e : qty.entrySet()) {
            if (e.getValue() < LOW) low.put(e.getKey(), e.getValue());
        }
        return low;
    }

    /** All or nothing. */
    synchronized TakeResult tryTake(Map<Product, Integer> items) {
        EnumMap<Product, Integer> shortages = new EnumMap<>(Product.class);
        for (var e : items.entrySet()) {
            int have = qty.get(e.getKey());
            if (have < e.getValue()) shortages.put(e.getKey(), have);
        }
        if (!shortages.isEmpty()) return new TakeResult(shortages, Collections.emptyMap());

        EnumMap<Product, Integer> low = new EnumMap<>(Product.class);
        for (var e : items.entrySet()) {
            int left = qty.merge(e.getKey(), -e.getValue(), Integer::sum);
            if (left < LOW && alerted.add(e.getKey())) low.put(e.getKey(), left);
        }
        return new TakeResult(Collections.emptyMap(), low);
    }

    /** Used by deliveries and by cancelled orders (products go back). */
    synchronized void add(Map<Product, Integer> items) {
        for (var e : items.entrySet()) {
            int now = (int) Math.min((long) qty.get(e.getKey()) + e.getValue(), Integer.MAX_VALUE);
            qty.put(e.getKey(), now);
            if (now >= LOW) alerted.remove(e.getKey());
        }
    }
}
