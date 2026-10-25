package com.daniellaera.cartservice.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * A customer's cart. It lives for LIFETIME after its last change; ttlSeconds is what is left of it. A cart that is
 * not stored at all (never filled, emptied, or expired) is "empty", which is not the same as a stored cart whose
 * items could not be read.
 */
public record Cart(String userEmail, List<CartItem> items, long ttlSeconds, boolean stored) {

    public static final Duration LIFETIME = Duration.ofSeconds(1800);

    public static Cart empty(String userEmail) {
        return new Cart(userEmail, List.of(), 0, false);
    }

    public BigDecimal total() {
        return items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // "29m 59s", "45s", "Expired" when nothing is left, "Cart is empty" when nothing is stored
    public String expiresIn() {
        if (!stored) {
            return "Cart is empty";
        }
        if (ttlSeconds <= 0) {
            return "Expired";
        }
        long minutes = ttlSeconds / 60;
        long secs = ttlSeconds % 60;
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }
}
