package com.daniellaera.inventoryservice.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records stock actually deducted for an order item when inventory APPROVED the order.
 * Compensation releases only what is recorded here, so a FAILED payment for an order
 * that was never reserved (e.g. REJECTED by inventory) releases nothing.
 */
@Entity
@Table(name = "stock_reservation")
@Getter
@Setter
@NoArgsConstructor
public class StockReservationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private LocalDateTime reservedAt = LocalDateTime.now();
}
