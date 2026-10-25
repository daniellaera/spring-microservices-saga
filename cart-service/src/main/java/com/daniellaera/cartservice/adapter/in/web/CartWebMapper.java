package com.daniellaera.cartservice.adapter.in.web;

import com.daniellaera.cartservice.adapter.in.web.dto.AddToCartRequest;
import com.daniellaera.cartservice.adapter.in.web.dto.CartItemResponse;
import com.daniellaera.cartservice.adapter.in.web.dto.CartResponse;
import com.daniellaera.cartservice.domain.model.Cart;
import com.daniellaera.cartservice.domain.model.CartItem;

final class CartWebMapper {

    private CartWebMapper() {
    }

    static CartItem toItem(AddToCartRequest request) {
        return new CartItem(request.productName(), request.quantity(), request.price());
    }

    static CartResponse toResponse(Cart cart) {
        return new CartResponse(cart.userEmail(),
                cart.items().stream()
                        .map(item -> new CartItemResponse(item.productName(), item.quantity(), item.price()))
                        .toList(),
                cart.total(), cart.ttlSeconds(), cart.expiresIn());
    }
}
