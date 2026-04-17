package com.daniellaera.gatewayservice.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping(value = "/orders", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> ordersFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "message", "Order service is currently unavailable. Please try again later."
                ));
    }

    @RequestMapping(value = "/products", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> productsFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "message", "Inventory service is currently unavailable. Please try again later."
                ));
    }

    @RequestMapping(value = "/transactions", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, String>> transactionsFallback() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "503",
                        "message", "Payment service is currently unavailable. Please try again later."
                ));
    }
}