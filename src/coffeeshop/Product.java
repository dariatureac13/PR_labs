package coffeeshop;

import java.util.Locale;

/** The three products of the shop and their preparation times. */
enum Product {
    COFFEE(4), TEA(3), CROISSANT(2);

    final int prepSeconds;

    Product(int prepSeconds) {
        this.prepSeconds = prepSeconds;
    }

    String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Returns the product with this name (case-insensitive) or null. */
    static Product parse(String s) {
        for (Product p : values()) {
            if (p.label().equalsIgnoreCase(s)) return p;
        }
        return null;
    }
}