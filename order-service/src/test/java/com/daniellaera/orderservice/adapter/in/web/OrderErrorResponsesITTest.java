package com.daniellaera.orderservice.adapter.in.web;

import com.daniellaera.orderservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the exact error bodies of the REST API. Only HTTP is used, so the same test passes against the code
 * before the hexagonal refactor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderErrorResponsesITTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownOrder_returns404Body() throws Exception {
        mockMvc.perform(get("/orders/987654321").header("X-User-Email", "user@test.com"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("{\"status\":404,\"message\":\"Order not found with id: 987654321\"}"));
    }

    @Test
    void otherUsersOrder_returns403Body() throws Exception {
        String created = mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"iPad\",\"quantity\":1,\"price\":599.99}")
                        .header("X-User-Email", "owner@test.com"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = created.replaceAll("^\\{\"id\":(\\d+),.*$", "$1");

        mockMvc.perform(get("/orders/" + id).header("X-User-Email", "intruder@test.com"))
                .andExpect(status().isForbidden())
                .andExpect(content().string("{\"status\":403,\"message\":\"Access denied\"}"));
    }

    @Test
    void emptyOrder_returns400Body() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"status\":400,\"message\":\"Order must have at least one item\"}"));
    }

    @Test
    void invalidItem_returnsValidationBody() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productName\":\"iPhone 16\",\"quantity\":0,\"price\":999.99}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("{\"status\":400,\"error\":\"Validation Failed\","
                        + "\"errors\":{\"items[0].quantity\":\"Quantity must be at least 1\"}}"));
    }

    @Test
    void streamWithoutEmail_returns401Body() throws Exception {
        mockMvc.perform(get("/orders/stream"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("{\"status\":401,\"message\":null}"));
    }
}
