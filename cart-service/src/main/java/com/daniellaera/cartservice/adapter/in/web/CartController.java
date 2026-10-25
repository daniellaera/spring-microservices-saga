package com.daniellaera.cartservice.adapter.in.web;

import com.daniellaera.cartservice.adapter.in.web.dto.AddToCartRequest;
import com.daniellaera.cartservice.adapter.in.web.dto.CartResponse;
import com.daniellaera.cartservice.domain.port.in.AddToCartUseCase;
import com.daniellaera.cartservice.domain.port.in.ClearCartUseCase;
import com.daniellaera.cartservice.domain.port.in.GetCartQuery;
import com.daniellaera.cartservice.domain.port.in.RemoveFromCartUseCase;
import com.daniellaera.cartservice.domain.port.in.UpdateCartItemUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final GetCartQuery getCartQuery;
    private final AddToCartUseCase addToCartUseCase;
    private final UpdateCartItemUseCase updateCartItemUseCase;
    private final RemoveFromCartUseCase removeFromCartUseCase;
    private final ClearCartUseCase clearCartUseCase;

    @GetMapping
    public ResponseEntity<CartResponse> getCart(@RequestHeader("X-User-Email") String userEmail) {
        return ResponseEntity.ok(CartWebMapper.toResponse(getCartQuery.getCart(userEmail)));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(
            @RequestHeader("X-User-Email") String userEmail,
            @Valid @RequestBody AddToCartRequest request) {
        return ResponseEntity.ok(CartWebMapper.toResponse(
                addToCartUseCase.addItem(userEmail, CartWebMapper.toItem(request))));
    }

    @PutMapping("/items/{productName}")
    public ResponseEntity<CartResponse> updateQuantity(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable(name = "productName") String productName,
            @RequestParam(name = "quantity") Integer quantity) {
        return ResponseEntity.ok(CartWebMapper.toResponse(
                updateCartItemUseCase.updateQuantity(userEmail, productName, quantity)));
    }

    @DeleteMapping("/items/{productName}")
    public ResponseEntity<CartResponse> removeItem(
            @RequestHeader("X-User-Email") String userEmail,
            @PathVariable(name = "productName") String productName) {
        return ResponseEntity.ok(CartWebMapper.toResponse(removeFromCartUseCase.removeItem(userEmail, productName)));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@RequestHeader("X-User-Email") String userEmail) {
        clearCartUseCase.clearCart(userEmail);
        return ResponseEntity.noContent().build();
    }
}
