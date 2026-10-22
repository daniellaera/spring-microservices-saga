package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ProductNotFoundException;
import com.daniellaera.inventoryservice.domain.port.in.ManageProductsUseCase;
import com.daniellaera.inventoryservice.domain.port.in.ProductQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductQuery productQuery;

    @Mock
    private ManageProductsUseCase manageProductsUseCase;

    private MockMvc mockMvc;

    private final Product product = Product.restore(1L, "MacBook Pro", 10, BigDecimal.valueOf(1299.99), LocalDateTime.now());

    @BeforeEach
    void setUp() {
        // not proxied, so no caching here: CachedProductCatalogCachingTest covers the cache
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProductController(new CachedProductCatalog(productQuery, manageProductsUseCase)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllProducts_shouldReturn200() throws Exception {
        when(productQuery.getAllProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("MacBook Pro"))
                .andExpect(jsonPath("$[0].quantity").value(10))
                .andExpect(jsonPath("$[0].price").value(1299.99));
    }

    @Test
    void getProductById_shouldReturn200() throws Exception {
        when(productQuery.getProductById(1L)).thenReturn(product);

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MacBook Pro"));
    }

    @Test
    void getProductById_shouldReturn404_whenNotFound() throws Exception {
        when(productQuery.getProductById(999L))
                .thenThrow(ProductNotFoundException.withId(999L));

        mockMvc.perform(get("/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id: 999"));
    }

    @Test
    void createProduct_shouldReturn200() throws Exception {
        when(manageProductsUseCase.createProduct(eq("MacBook Pro"), eq(10), any())).thenReturn(product);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MacBook Pro\",\"quantity\":10,\"price\":1299.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MacBook Pro"))
                .andExpect(jsonPath("$.quantity").value(10));
    }

    @Test
    void restock_shouldReturn200AndUpdatedQuantity() throws Exception {
        Product restocked = Product.restore(1L, "MacBook Pro", 25, BigDecimal.valueOf(1299.99), LocalDateTime.now());
        when(manageProductsUseCase.restock(1L, 15)).thenReturn(restocked);

        mockMvc.perform(put("/products/1/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MacBook Pro"))
                .andExpect(jsonPath("$.quantity").value(25));
    }

    @Test
    void restock_shouldReturn404_whenProductNotFound() throws Exception {
        when(manageProductsUseCase.restock(999L, 10))
                .thenThrow(ProductNotFoundException.forRestock(999L));

        mockMvc.perform(put("/products/999/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found: 999"));
    }
}
