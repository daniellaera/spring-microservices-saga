package com.daniellaera.frontendservice.controller;

import com.daniellaera.frontendservice.client.GatewayClient;
import com.daniellaera.frontendservice.dto.OrderDto;
import com.daniellaera.frontendservice.dto.ProductDto;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ShopController {

    private final GatewayClient gatewayClient;
    private final ObjectMapper objectMapper;

    @GetMapping("/")
    public String index(HttpSession session, Model model) throws Exception {
        String token = (String) session.getAttribute("token");
        if (token == null) return "redirect:/login";

        // extraire le rôle du JWT
        String role = extractRoleFromToken(token);
        model.addAttribute("role", role);

        List<OrderDto> orders = objectMapper.readValue(
                gatewayClient.getOrders(token),
                new TypeReference<List<OrderDto>>() {}
        );
        List<ProductDto> products = objectMapper.readValue(
                gatewayClient.getProducts(token),
                new TypeReference<List<ProductDto>>() {}
        );

        model.addAttribute("orders", orders);
        model.addAttribute("products", products);
        return "index";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam("email") String email,
                        @RequestParam("password") String password,
                        HttpSession session) {
        try {
            String response = gatewayClient.login(email, password);
            String token = response.replace("{\"token\":\"", "").replace("\"}", "");
            session.setAttribute("token", token);
            return "redirect:/";
        } catch (Exception e) {
            return "redirect:/login?error=true";
        }
    }

    @PostMapping("/products")
    public String createProduct(@RequestParam("name") String name,
                                @RequestParam("quantity") int quantity,
                                HttpSession session) {
        String token = (String) session.getAttribute("token");
        if (token == null) return "redirect:/login";
        gatewayClient.createProduct(token, name, quantity);
        return "redirect:/";
    }

    @PostMapping("/orders")
    public String createOrder(@RequestParam("productName") String productName,
                              @RequestParam("quantity") int quantity,
                              HttpSession session) {
        String token = (String) session.getAttribute("token");
        if (token == null) return "redirect:/login";
        gatewayClient.createOrder(token, productName, quantity);
        return "redirect:/";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    private String extractRoleFromToken(String token) {
        try {
            String payload = token.split("\\.")[1];
            String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload));
            // parse "role":"ADMIN" or "role":"USER"
            tools.jackson.databind.JsonNode node = objectMapper.readTree(decoded);
            return node.get("role").asString();
        } catch (Exception e) {
            return "USER";
        }
    }
}
