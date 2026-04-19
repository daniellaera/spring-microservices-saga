package com.daniellaera.orderservice.controller;

import com.daniellaera.orderservice.TestcontainersConfiguration;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.model.Order;
import com.daniellaera.orderservice.repository.OrderRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class OrderControllerITTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        Order order = new Order();
        order.setProductName("MacBook Pro");
        order.setQuantity(1);
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);
    }

    @Test
    void getAllOrders_shouldReturn200AndList() throws Exception {
        mockMvc.perform(get("/orders")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productName").value("MacBook Pro"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getOrderById_shouldReturn200() throws Exception {
        Order saved = orderRepository.findAll().get(0);

        mockMvc.perform(get("/orders/" + saved.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("MacBook Pro"));
    }

    @Test
    void getOrderById_shouldReturn404_whenNotFound() throws Exception {
        mockMvc.perform(get("/orders/99999")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOrder_shouldReturn200AndPersist() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"iPhone 16\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("iPhone 16"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertThat(orderRepository.findAll()).hasSize(2);
    }
}