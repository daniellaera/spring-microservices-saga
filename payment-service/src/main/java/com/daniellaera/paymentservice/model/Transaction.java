package com.daniellaera.paymentservice.model;

import com.daniellaera.paymentservice.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
public class Transaction extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalAmount;
}