package com.daniellaera.cartservice.domain.port.in;

import com.daniellaera.cartservice.domain.model.Cart;

public interface GetCartQuery {

    Cart getCart(String userEmail);
}
