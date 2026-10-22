package com.daniellaera.orderservice.domain.port.out;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);

    Optional<Order> findById(long id);

    /** Sorted by createdAt descending. */
    PageResult<Order> findByUserEmail(String userEmail, PageQuery page);

    /** Sorted by createdAt descending. */
    PageResult<Order> findAll(PageQuery page);
}
