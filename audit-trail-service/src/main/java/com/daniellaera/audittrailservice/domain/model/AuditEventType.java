package com.daniellaera.audittrailservice.domain.model;

/**
 * Every event type the services send to audit-topic. A message with any other type is logged and dropped by
 * AuditService, so a producer's new type must be added here too: AuditEventTypeTest lists them all.
 */
public enum AuditEventType {
    // Auth events
    USER_LOGIN,
    USER_LOGOUT,
    USER_REGISTER,
    USER_LOGIN_FAILED,
    USER_LOGIN_INITIATED,
    USER_PROFILE_UPDATED,
    TOKEN_REFRESHED,

    // Order events
    ORDER_CREATED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ORDER_DEAD_LETTERED,

    // Payment events
    PAYMENT_INITIATED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED,
    PAYMENT_REFUND_FAILED,
    PAYMENT_DEAD_LETTERED,

    // Inventory events
    PRODUCT_CREATED,
    PRODUCT_RESTOCKED,
    STOCK_DEDUCTED,
    STOCK_RESTORED,
    STOCK_RESERVATION_DEAD_LETTERED,
    STOCK_RESTORE_DEAD_LETTERED,

    // Outbox events (order-service)
    OUTBOX_EVENT_FAILED,

    // Cart events
    CART_ITEM_ADDED,
    CART_CLEARED,
    CART_CHECKOUT
}
