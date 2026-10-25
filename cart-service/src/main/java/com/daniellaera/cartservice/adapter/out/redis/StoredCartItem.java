package com.daniellaera.cartservice.adapter.out.redis;

import java.math.BigDecimal;

// The JSON stored per product in the cart's hash: these three properties and nothing else (no type information)
record StoredCartItem(String productName, Integer quantity, BigDecimal price) {
}
