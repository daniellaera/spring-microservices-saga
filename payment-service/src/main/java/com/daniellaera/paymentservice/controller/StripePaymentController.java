package com.daniellaera.paymentservice.controller;

import com.daniellaera.paymentservice.dto.PaymentIntentRequest;
import com.daniellaera.paymentservice.dto.PaymentIntentResponse;
import com.daniellaera.paymentservice.service.StripePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Slf4j
public class StripePaymentController {

    private final StripePaymentService stripePaymentService;

    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(@RequestBody PaymentIntentRequest request) {
        return ResponseEntity.ok(stripePaymentService.createPaymentIntent(request));
    }

    @GetMapping("/confirm/{paymentIntentId}")
    public ResponseEntity<Map<String, Boolean>> confirmPayment(@PathVariable String paymentIntentId) {
        boolean success = stripePaymentService.confirmPayment(paymentIntentId);
        return ResponseEntity.ok(Map.of("success", success));
    }
}
