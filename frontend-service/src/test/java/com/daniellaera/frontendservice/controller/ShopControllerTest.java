package com.daniellaera.frontendservice.controller;

import com.daniellaera.frontendservice.client.GatewayClient;
import com.daniellaera.frontendservice.dto.OrderDto;
import com.daniellaera.frontendservice.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ShopControllerTest {

    @InjectMocks
    private ShopController shopController;

    @Mock
    private GatewayClient gatewayClient;

    @Mock
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(shopController)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void index_shouldRedirectToLogin_whenNoToken() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void index_shouldReturnIndexView_whenTokenPresent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQHRlc3QuY29tIiwicm9sZSI6IlVTRVIifQ.signature";
        session.setAttribute("token", fakeToken);

        when(gatewayClient.getOrders(anyString())).thenReturn("[]");
        when(gatewayClient.getProducts(anyString())).thenReturn("[]");
        when(objectMapper.readValue(eq("[]"), any(tools.jackson.core.type.TypeReference.class)))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void index_shouldExposeOrdersWithPriceAndTotalAmount() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQHRlc3QuY29tIiwicm9sZSI6IlVTRVIifQ.signature";
        session.setAttribute("token", fakeToken);

        List<OrderDto> orders = List.of(
                new OrderDto(1L, "MacBook Pro", 2, new BigDecimal("999.99"), new BigDecimal("1999.98"), "CONFIRMED")
        );
        List<ProductDto> products = List.of();

        when(gatewayClient.getOrders(anyString())).thenReturn("[order]");
        when(gatewayClient.getProducts(anyString())).thenReturn("[]");
        when(objectMapper.readValue(eq("[order]"), any(tools.jackson.core.type.TypeReference.class)))
                .thenReturn(orders);
        when(objectMapper.readValue(eq("[]"), any(tools.jackson.core.type.TypeReference.class)))
                .thenReturn(products);

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("orders"))
                .andExpect(model().attribute("orders", orders));
    }

    @Test
    void index_shouldExposeProductsWithPrice() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQHRlc3QuY29tIiwicm9sZSI6IlVTRVIifQ.signature";
        session.setAttribute("token", fakeToken);

        List<OrderDto> orders = List.of();
        List<ProductDto> products = List.of(
                new ProductDto("MacBook Pro", 10, new BigDecimal("999.99"))
        );

        when(gatewayClient.getOrders(anyString())).thenReturn("[]");
        when(gatewayClient.getProducts(anyString())).thenReturn("[product]");
        when(objectMapper.readValue(eq("[]"), any(tools.jackson.core.type.TypeReference.class)))
                .thenReturn(orders);
        when(objectMapper.readValue(eq("[product]"), any(tools.jackson.core.type.TypeReference.class)))
                .thenReturn(products);

        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("products"))
                .andExpect(model().attribute("products", products));
    }

    @Test
    void ordersPartial_shouldRedirectToLogin_whenNoToken() throws Exception {
        mockMvc.perform(get("/orders/partial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void ordersPartial_shouldReturnFragment_whenTokenPresent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQHRlc3QuY29tIiwicm9sZSI6IlVTRVIifQ.signature";
        session.setAttribute("token", fakeToken);

        List<OrderDto> orders = List.of(
                new OrderDto(1L, "MacBook Pro", 2, new BigDecimal("999.99"), new BigDecimal("1999.98"), "PENDING")
        );

        when(gatewayClient.getOrders(anyString())).thenReturn("[order]");
        when(objectMapper.readValue(eq("[order]"), any(tools.jackson.core.type.TypeReference.class)))
                .thenReturn(orders);

        mockMvc.perform(get("/orders/partial").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/orders :: orders-body"))
                .andExpect(model().attributeExists("orders"))
                .andExpect(model().attribute("orders", orders));
    }

    @Test
    void productsPartial_shouldRedirectToLogin_whenNoToken() throws Exception {
        mockMvc.perform(get("/products/partial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void productsPartial_shouldReturnFragment_whenTokenPresent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String fakeToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyQHRlc3QuY29tIiwicm9sZSI6IlVTRVIifQ.signature";
        session.setAttribute("token", fakeToken);

        List<ProductDto> products = List.of(
                new ProductDto("MacBook Pro", 10, new BigDecimal("999.99"))
        );

        when(gatewayClient.getProducts(anyString())).thenReturn("[product]");
        when(objectMapper.readValue(eq("[product]"), any(tools.jackson.core.type.TypeReference.class)))
                .thenReturn(products);

        mockMvc.perform(get("/products/partial").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/products :: products-body"))
                .andExpect(model().attributeExists("products"))
                .andExpect(model().attribute("products", products));
    }

    @Test
    void loginPage_shouldReturnLoginView() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    void login_shouldRedirectToHome_onSuccess() throws Exception {
        when(gatewayClient.login(anyString(), anyString()))
                .thenReturn("{\"token\":\"mocked-token\"}");

        mockMvc.perform(post("/login")
                        .param("email", "user@test.com")
                        .param("password", "password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void login_shouldRedirectWithError_onFailure() throws Exception {
        when(gatewayClient.login(anyString(), anyString()))
                .thenThrow(new RuntimeException("Unauthorized"));

        mockMvc.perform(post("/login")
                        .param("email", "bad@test.com")
                        .param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void logout_shouldInvalidateSessionAndRedirect() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("token", "some-token");

        mockMvc.perform(get("/logout").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void createOrder_shouldRedirectToLogin_whenNoToken() throws Exception {
        mockMvc.perform(post("/orders")
                        .param("productName", "MacBook Pro")
                        .param("quantity", "1")
                        .param("price", "999.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void createOrder_shouldRedirectToHome_whenTokenPresent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("token", "valid-token");

        mockMvc.perform(post("/orders").session(session)
                        .param("productName", "MacBook Pro")
                        .param("quantity", "1")
                        .param("price", "999.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(gatewayClient, times(1)).createOrder(anyString(), anyString(), anyInt(), any(BigDecimal.class));
    }

    @Test
    void createProduct_shouldRedirectToLogin_whenNoToken() throws Exception {
        mockMvc.perform(post("/products")
                        .param("name", "iPhone")
                        .param("quantity", "5")
                        .param("price", "299.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void createProduct_shouldRedirectToHome_whenTokenPresent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("token", "admin-token");

        mockMvc.perform(post("/products").session(session)
                        .param("name", "iPhone")
                        .param("quantity", "5")
                        .param("price", "299.99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(gatewayClient, times(1)).createProduct(anyString(), anyString(), anyInt(), any(BigDecimal.class));
    }
}
