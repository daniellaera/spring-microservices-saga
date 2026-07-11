package com.daniellaera.cartservice.controller;

import com.daniellaera.cartservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CartControllerITTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String USER_EMAIL = "cart-it@example.com";
    private static final String CART_KEY = "cart:" + USER_EMAIL;

    private void clearCartKey() {
        redisTemplate.delete(CART_KEY);
    }

    @Test
    void addItem_thenGetCart_showsItemWithPositiveTtl() throws Exception {
        clearCartKey();

        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"MacBook Pro\",\"quantity\":1,\"price\":1299.99}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/cart").header("X-User-Email", USER_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("MacBook Pro"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andExpect(jsonPath("$.total").value(1299.99));

        Long ttl = redisTemplate.getExpire(CART_KEY, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0).isLessThanOrEqualTo(1800);
    }

    @Test
    void addSameItemTwice_accumulatesQuantity() throws Exception {
        clearCartKey();

        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"AirPods\",\"quantity\":1,\"price\":249.99}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"AirPods\",\"quantity\":2,\"price\":249.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void updateQuantityToZero_removesItem() throws Exception {
        clearCartKey();

        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"iPad\",\"quantity\":2,\"price\":599.99}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/cart/items/iPad")
                        .header("X-User-Email", USER_EMAIL)
                        .param("quantity", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/cart").header("X-User-Email", USER_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void clearCart_removesAllItems() throws Exception {
        clearCartKey();

        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"iPhone 16\",\"quantity\":1,\"price\":999.99}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/cart").header("X-User-Email", USER_EMAIL))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/cart").header("X-User-Email", USER_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.expiresIn").value("Cart is empty"));

        assertThat(redisTemplate.hasKey(CART_KEY)).isFalse();
    }

    @Test
    void removeItem_resetsTtlOnRemainingKey() throws Exception {
        clearCartKey();

        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Case\",\"quantity\":1,\"price\":19.99}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/cart/items")
                        .header("X-User-Email", USER_EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"Charger\",\"quantity\":1,\"price\":29.99}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/cart/items/Case").header("X-User-Email", USER_EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productName").value("Charger"));

        Long ttl = redisTemplate.getExpire(CART_KEY, TimeUnit.SECONDS);
        assertThat(ttl).isGreaterThan(0);
    }
}
