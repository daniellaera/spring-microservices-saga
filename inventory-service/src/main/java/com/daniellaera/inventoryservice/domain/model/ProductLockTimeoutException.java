package com.daniellaera.inventoryservice.domain.model;

/**
 * Another stock change held the product's row lock longer than the lock timeout. The caller's transaction is
 * rolled back; nothing it changed is kept.
 */
public class ProductLockTimeoutException extends RuntimeException {

    public ProductLockTimeoutException(String product, Throwable cause) {
        super("Product " + product + " is locked by another stock change", cause);
    }
}
