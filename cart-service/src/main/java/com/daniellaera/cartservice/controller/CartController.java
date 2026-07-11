package com.daniellaera.cartservice.controller;

import com.daniellaera.cartservice.dto.AddToCartRequest;
import com.daniellaera.cartservice.dto.CartResponse;
import com.daniellaera.cartservice.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(cartService.getCart(userEmail));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader("X-User-Email") String userEmail,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(cartService.addItem(userEmail, request));
    }

    @PutMapping("/items/{productName}")
    public ResponseEntity<CartResponse> updateQuantity(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable(name = "productName") String productName,
            @RequestParam(name = "quantity") Integer quantity) {
        return ResponseEntity.ok(cartService.updateQuantity(userEmail, productName, quantity));
    }

    @DeleteMapping("/items/{productName}")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable(name = "productName") String productName) {
        return ResponseEntity.ok(cartService.removeItem(userEmail, productName));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@RequestHeader("X-User-Email") String userEmail) {
        cartService.clearCart(userEmail);
        return ResponseEntity.noContent().build();
    }
}
