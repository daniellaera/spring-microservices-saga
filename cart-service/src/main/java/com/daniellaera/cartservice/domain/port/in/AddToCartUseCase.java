package com.daniellaera.cartservice.domain.port.in;

import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;

/** Adds the item; a product already in the cart gets the quantities summed and the new price. */
public interface AddToCartUseCase {

    Cart addItem(String userEmail, CartItem item);
}
