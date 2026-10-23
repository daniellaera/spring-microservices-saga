package com.daniellaera.inventoryservice.domain.port.out;

/**
 * The product list shown to customers is cached. Stock changed by an order or a compensation must invalidate
 * it. Never throws; inside a transaction, the list is invalidated only after the commit.
 */
public interface ProductListCache {

    void invalidate();
}
