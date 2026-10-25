package com.daniellaera.cartservice.domain.port.in;

import com.daniellaera.cartservice.domain.model.Cart;

/** Sets a product's quantity, keeping its price; zero or less removes it; a product not in the cart is ignored. */
public interface UpdateCartItemUseCase {

    Cart updateQuantity(String userEmail, String productName, Integer quantity);
}
