package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the exact error bodies of the REST API. Only HTTP is used, so the same test passes against the code
 * before the hexagonal refactor. Note the two different 404 messages of GET and restock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductErrorResponsesITTest {

    @Autowired
    private MockMvc mockMvc;

    // same context as ProductControllerITTest; also keeps audit events off a real broker
    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void unknownProduct_returns404Body() throws Exception {
        mockMvc.perform(get("/products/987654321"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("{\"status\":404,\"message\":\"Product not found with id: 987654321\"}"));
    }

    @Test
    void restockUnknownProduct_returns404BodyWithItsOwnMessage() throws Exception {
        mockMvc.perform(put("/products/987654321/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5}"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("{\"status\":404,\"message\":\"Product not found: 987654321\"}"));
    }

    @Test
    void duplicateProduct_returns409Body() throws Exception {
        // other ITs share the database, so use a name nobody else creates
        String name = "Duplicate Probe " + System.nanoTime();
        String body = "{\"name\":\"" + name + "\",\"quantity\":1,\"price\":9.99}";
        mockMvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().string("{\"status\":409,\"message\":\"Product already exists: " + name + "\"}"));
    }

    @Test
    void invalidProduct_returnsValidationBody() throws Exception {
        mockMvc.perform(post("/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Widget\",\"quantity\":-1,\"price\":9.99}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"status\":400,\"error\":\"Validation Failed\","
                        + "\"errors\":{\"quantity\":\"Quantity must be at least 0\"}}"));
    }

    @Test
    void invalidRestock_returnsValidationBody() throws Exception {
        mockMvc.perform(put("/products/1/restock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"status\":400,\"error\":\"Validation Failed\","
                        + "\"errors\":{\"quantity\":\"Quantity must be at least 1\"}}"));
    }
}
