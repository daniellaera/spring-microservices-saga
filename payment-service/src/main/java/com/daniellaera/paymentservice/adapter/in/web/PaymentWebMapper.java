package com.daniellaera.paymentservice.adapter.in.web;

import com.daniellaera.paymentservice.adapter.in.web.dto.PaymentIntentRequest;
import com.daniellaera.paymentservice.adapter.in.web.dto.PaymentIntentResponse;
import com.daniellaera.paymentservice.adapter.in.web.dto.TransactionDTO;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;

public final class PaymentWebMapper {

    private PaymentWebMapper() {
    }

    public static TransactionDTO toDto(PaymentTransaction t) {
        return new TransactionDTO(t.id(), t.orderId(), t.totalAmount(), t.status());
    }

    public static CreatePaymentIntentCommand toCommand(PaymentIntentRequest request, String userEmail) {
        return new CreatePaymentIntentCommand(request.amount(), request.currency(), request.productName(), userEmail);
    }

    // The publishable key is handed to the browser; it is configuration, not part of the use case
    public static PaymentIntentResponse toResponse(CreatedPaymentIntent intent, String publishableKey) {
        return new PaymentIntentResponse(intent.clientSecret(), intent.id(), intent.status(), publishableKey);
    }
}
