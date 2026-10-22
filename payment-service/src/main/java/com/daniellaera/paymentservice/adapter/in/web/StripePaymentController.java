package com.daniellaera.paymentservice.adapter.in.web;

import com.daniellaera.paymentservice.adapter.in.web.dto.PaymentIntentRequest;
import com.daniellaera.paymentservice.adapter.in.web.dto.PaymentIntentResponse;
import com.daniellaera.paymentservice.config.StripeConfig;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentUseCase;
import com.daniellaera.paymentservice.domain.port.in.VerifyPaymentQuery;
import jakarta.validation.Valid;
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

    private final CreatePaymentIntentUseCase createPaymentIntentUseCase;
    private final VerifyPaymentQuery verifyPaymentQuery;
    private final StripeConfig stripeConfig;

    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(
            @Valid @RequestBody PaymentIntentRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.ok(PaymentWebMapper.toResponse(
                createPaymentIntentUseCase.createIntent(PaymentWebMapper.toCommand(request, userEmail)),
                stripeConfig.getPublishableKey()));
    }

    @GetMapping("/confirm/{paymentIntentId}")
    public ResponseEntity<Map<String, Boolean>> confirmPayment(@PathVariable("paymentIntentId") String paymentIntentId) {
        boolean success = verifyPaymentQuery.isSucceeded(paymentIntentId);
        return ResponseEntity.ok(Map.of("success", success));
    }
}
