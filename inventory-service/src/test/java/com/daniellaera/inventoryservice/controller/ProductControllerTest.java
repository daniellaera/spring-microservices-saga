package com.daniellaera.inventoryservice.controller;

import com.daniellaera.inventoryservice.dto.ProductDTO;
import com.daniellaera.inventoryservice.dto.ProductRequest;
import com.daniellaera.inventoryservice.exception.GlobalExceptionHandler;
import com.daniellaera.inventoryservice.exception.ResourceNotFoundException;
import com.daniellaera.inventoryservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @InjectMocks
    private ProductController productController;

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    private final ProductDTO dto = new ProductDTO(1L, "MacBook Pro", 10, LocalDateTime.now());

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(productController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllProducts_shouldReturn200() throws Exception {
        when(productService.getAllProducts()).thenReturn(List.of(dto));

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("MacBook Pro"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    void getProductById_shouldReturn200() throws Exception {
        when(productService.getProductById(1L)).thenReturn(dto);

        mockMvc.perform(get("/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MacBook Pro"));
    }

    @Test
    void getProductById_shouldReturn404_whenNotFound() throws Exception {
        when(productService.getProductById(999L))
                .thenThrow(new ResourceNotFoundException("Product not found with id: 999"));

        mockMvc.perform(get("/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id: 999"));
    }

    @Test
    void createProduct_shouldReturn200() throws Exception {
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(dto);

        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"MacBook Pro\",\"quantity\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MacBook Pro"))
                .andExpect(jsonPath("$.quantity").value(10));
    }
}