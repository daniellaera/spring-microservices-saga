package com.daniellaera.inventoryservice.controller;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import com.daniellaera.inventoryservice.model.Product;
import com.daniellaera.inventoryservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class ProductControllerITTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();

        Product product = new Product();
        product.setName("MacBook Pro");
        product.setQuantity(10);
        productRepository.save(product);
    }

    @Test
    void getAllProducts_shouldReturn200AndList() throws Exception {
        mockMvc.perform(get("/products")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("MacBook Pro"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    void getProductById_shouldReturn200() throws Exception {
        Product saved = productRepository.findAll().getFirst();

        mockMvc.perform(get("/products/" + saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("MacBook Pro"));
    }

    @Test
    void getProductById_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get("/products/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99999"));
    }

    @Test
    void createProduct_shouldReturn200AndPersist() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"iPhone 16\",\"quantity\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("iPhone 16"))
                .andExpect(jsonPath("$.quantity").value(5));

        assertThat(productRepository.findAll()).hasSize(2);
    }
}