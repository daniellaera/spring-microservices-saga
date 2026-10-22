package com.daniellaera.paymentservice.adapter.in.web;

import com.daniellaera.paymentservice.adapter.in.web.dto.TransactionDTO;
import com.daniellaera.paymentservice.domain.port.in.TransactionQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class PaymentController {

    private final TransactionQuery transactionQuery;

    @GetMapping
    public ResponseEntity<List<TransactionDTO>> getAllTransactions() {
        return ResponseEntity.ok(transactionQuery.getAllTransactions().stream().map(PaymentWebMapper::toDto).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDTO> getTransactionById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(PaymentWebMapper.toDto(transactionQuery.getTransactionById(id)));
    }
}
