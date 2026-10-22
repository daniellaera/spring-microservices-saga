package com.daniellaera.paymentservice.service;

import org.springframework.stereotype.Service;

@Service
public interface PaymentService {
    void handleInventoryResult(String message) throws Exception;
}