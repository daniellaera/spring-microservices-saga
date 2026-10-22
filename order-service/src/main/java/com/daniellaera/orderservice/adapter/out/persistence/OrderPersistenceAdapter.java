package com.daniellaera.orderservice.adapter.out.persistence;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderNotFoundException;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;
import com.daniellaera.orderservice.domain.port.out.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderPersistenceAdapter implements OrderRepository {

    private final OrderJpaRepository orderJpaRepository;

    @Override
    @Transactional
    public Order save(Order order) {
        if (order.id() == null) {
            return OrderJpaMapper.toDomain(orderJpaRepository.save(OrderJpaMapper.toNewEntity(order)));
        }
        // Existing orders only ever change status: update the managed entity, never rebuild the graph
        OrderJpaEntity entity = orderJpaRepository.findById(order.id())
                .orElseThrow(() -> new OrderNotFoundException(order.id()));
        entity.setStatus(order.status());
        return OrderJpaMapper.toDomain(orderJpaRepository.save(entity));
    }

    @Override
    public Optional<Order> findById(long id) {
        return orderJpaRepository.findById(id).map(OrderJpaMapper::toDomain);
    }

    @Override
    public PageResult<Order> findByUserEmail(String userEmail, PageQuery page) {
        return toPageResult(orderJpaRepository.findByUserEmail(userEmail, newestFirst(page)));
    }

    @Override
    public PageResult<Order> findAll(PageQuery page) {
        return toPageResult(orderJpaRepository.findAll(newestFirst(page)));
    }

    private static Pageable newestFirst(PageQuery page) {
        return PageRequest.of(page.page(), page.size(), Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static PageResult<Order> toPageResult(Page<OrderJpaEntity> result) {
        return new PageResult<>(
                result.getContent().stream().map(OrderJpaMapper::toDomain).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.hasNext(),
                result.hasPrevious()
        );
    }
}
