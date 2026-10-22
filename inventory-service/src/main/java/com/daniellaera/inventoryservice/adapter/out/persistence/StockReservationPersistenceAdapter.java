package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StockReservationPersistenceAdapter implements StockReservationRepository {

    private final StockReservationJpaRepository stockReservationJpaRepository;

    @Override
    public StockReservation save(StockReservation reservation) {
        return StockReservationJpaMapper.toDomain(
                stockReservationJpaRepository.save(StockReservationJpaMapper.toNewEntity(reservation)));
    }

    @Override
    public List<StockReservation> findByOrderId(long orderId) {
        return stockReservationJpaRepository.findByOrderId(orderId).stream()
                .map(StockReservationJpaMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByOrderId(long orderId) {
        return stockReservationJpaRepository.existsByOrderId(orderId);
    }
}
