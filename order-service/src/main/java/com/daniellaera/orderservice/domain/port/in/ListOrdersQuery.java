package com.daniellaera.orderservice.domain.port.in;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.model.PageResult;

public interface ListOrdersQuery {
    PageResult<Order> listOrdersOf(String userEmail, PageQuery page);

    PageResult<Order> listAllOrders(PageQuery page);
}
