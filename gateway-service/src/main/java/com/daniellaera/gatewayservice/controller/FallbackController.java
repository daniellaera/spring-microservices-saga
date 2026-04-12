package com.daniellaera.gatewayservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping("/orders")
    @PostMapping("/orders")
    public ResponseEntity<Map<String, String>> ordersFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "message", "Order service is currently unavailable. Please try again later."
                ));
    }

    @GetMapping("/products")
    @PostMapping("/products")
    public ResponseEntity<Map<String, String>> productsFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "message", "Inventory service is currently unavailable. Please try again later."
                ));
    }

    @GetMapping("/transactions")
    @PostMapping("/transactions")
    public ResponseEntity<Map<String, String>> transactionsFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "message", "Payment service is currently unavailable. Please try again later."
                ));
    }
}