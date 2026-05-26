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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

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
        order.setPrice(BigDecimal.valueOf(1299.99));
        order.setTotalAmount(BigDecimal.valueOf(1299.99));
        order.setStatus(OrderStatus.PENDING);
        orderRepository.save(order);
    }

    @Test
    void getAllOrders_shouldReturn200AndList() throws Exception {
        mockMvc.perform(get("/orders")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].productName").value("MacBook Pro"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
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
                        .content("{\"productName\":\"iPhone 16\",\"quantity\":2,\"price\":999.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productName").value("iPhone 16"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.totalAmount").value(1999.98));

        assertThat(orderRepository.findAll()).hasSize(2);
    }

    @Test
    void createOrder_withUserEmailHeader_shouldPersistEmail() throws Exception {
        mockMvc.perform(post("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productName\":\"AirPods\",\"quantity\":1,\"price\":249.99}")
                        .header("X-User-Email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userEmail").value("alice@example.com"));

        assertThat(orderRepository.findByUserEmail("alice@example.com")).hasSize(1);
    }

    @Test
    void getMyOrders_shouldReturnOnlyOrdersForThatEmail() throws Exception {
        Order alice = new Order();
        alice.setProductName("iPad");
        alice.setQuantity(1);
        alice.setPrice(BigDecimal.valueOf(599.99));
        alice.setTotalAmount(BigDecimal.valueOf(599.99));
        alice.setStatus(OrderStatus.PENDING);
        alice.setUserEmail("alice@example.com");
        orderRepository.save(alice);

        mockMvc.perform(get("/orders/my")
                        .header("X-User-Email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].userEmail").value("alice@example.com"))
                .andExpect(jsonPath("$.content[0].productName").value("iPad"));
    }

    @Test
    void getMyOrders_withoutHeader_shouldReturn401() throws Exception {
        mockMvc.perform(get("/orders/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void streamOrders_withEmail_returns200AndEventStream() throws Exception {
        mockMvc.perform(get("/orders/stream")
                        .header("X-User-Email", "alice@example.com")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    @Test
    void streamOrders_withoutEmail_returns401() throws Exception {
        mockMvc.perform(get("/orders/stream"))
                .andExpect(status().isUnauthorized());
    }
}
