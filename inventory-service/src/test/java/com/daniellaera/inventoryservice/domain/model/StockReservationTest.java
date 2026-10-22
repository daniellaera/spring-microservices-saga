package com.daniellaera.inventoryservice.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockReservationTest {

    @Test
    void of_leavesIdAndReservedAtToPersistence() {
        StockReservation reservation = StockReservation.of(42L, "MacBook Pro", 2);

        assertThat(reservation.id()).isNull();
        assertThat(reservation.reservedAt()).isNull();
        assertThat(reservation.orderId()).isEqualTo(42L);
        assertThat(reservation.productName()).isEqualTo("MacBook Pro");
        assertThat(reservation.quantity()).isEqualTo(2);
    }
}
