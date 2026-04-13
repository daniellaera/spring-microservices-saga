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
}
