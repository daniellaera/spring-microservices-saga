package com.daniellaera.frontendservice.controller;

import com.daniellaera.frontendservice.client.GatewayClient;
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
        // JWT token with role=USER payload
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
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void createOrder_shouldRedirectToHome_whenTokenPresent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("token", "valid-token");

        mockMvc.perform(post("/orders").session(session)
                        .param("productName", "MacBook Pro")
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(gatewayClient, times(1)).createOrder(anyString(), anyString(), anyInt());
    }

    @Test
    void createProduct_shouldRedirectToLogin_whenNoToken() throws Exception {
        mockMvc.perform(post("/products")
                        .param("name", "iPhone")
                        .param("quantity", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void createProduct_shouldRedirectToHome_whenTokenPresent() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("token", "admin-token");

        mockMvc.perform(post("/products").session(session)
                        .param("name", "iPhone")
                        .param("quantity", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(gatewayClient, times(1)).createProduct(anyString(), anyString(), anyInt());
    }
}