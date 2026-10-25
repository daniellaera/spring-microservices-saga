package com.daniellaera.cartservice.domain.port.in;

import com.daniellaera.cartservice.domain.model.Cart;

public interface RemoveFromCartUseCase {

    Cart removeItem(String userEmail, String productName);
}
