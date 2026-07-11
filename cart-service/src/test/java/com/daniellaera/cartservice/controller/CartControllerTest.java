package com.daniellaera.cartservice.controller;

import com.daniellaera.cartservice.dto.CartResponse;
import com.daniellaera.cartservice.exception.GlobalExceptionHandler;
import com.daniellaera.cartservice.model.CartItem;
import com.daniellaera.cartservice.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    @InjectMocks
    private CartController cartController;

    @Mock
    private CartService cartService;

    private MockMvc mockMvc;

    private static final String USER_EMAIL = "user@test.com";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void getCart_returns200WithCart() throws Exception {
        CartResponse cart = new CartResponse(USER_EMAIL, List.of(), BigDecimal.ZERO, 0, "Cart is empty");
        when(cartService.getCart(USER_EMAIL)).thenReturn(cart);

        mockMvc.perform(get("/cart").header("X-User-Email", USER_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value(USER_EMAIL));
    }

    @Test
    void addItem_withValidBody_returns200() throws Exception {
        CartItem item = new CartItem("MacBookPro", 1, BigDecimal.valueOf(999.99));
        CartResponse cart = new CartResponse(USER_EMAIL, List.of(item), BigDecimal.valueOf(999.99), 1800, "30m 0s");
        when(cartService.addItem(eq(USER_EMAIL), any(com.daniellaera.cartservice.dto.AddToCartRequest.class)))
                .thenReturn(cart);

        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"MacBook Pro\",\"quantity\":1,\"price\":999.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("MacBookPro"));
    }

    @Test
    void addItem_withInvalidBody_returns400() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"\",\"quantity\":0,\"price\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeItem_returns200() throws Exception {
        CartResponse cart = new CartResponse(USER_EMAIL, List.of(), BigDecimal.ZERO, 0, "Cart is empty");
        when(cartService.removeItem(USER_EMAIL, "MacBookPro")).thenReturn(cart);

        mockMvc.perform(delete("/cart/items/{productName}", "MacBookPro")
                        .header("X-User-Email", USER_EMAIL))
                .andExpect(status().isOk());
    }

    @Test
    void clearCart_returns204() throws Exception {
        mockMvc.perform(delete("/cart").header("X-User-Email", USER_EMAIL))
                .andExpect(status().isNoContent());
    }
}
