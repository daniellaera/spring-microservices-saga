package com.daniellaera.audittrailservice.enums;

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

    // Payment events
    PAYMENT_INITIATED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,

    // Inventory events
    PRODUCT_CREATED,
    PRODUCT_RESTOCKED,
    STOCK_DEDUCTED,
    STOCK_RESTORED,

    // Cart events
    CART_ITEM_ADDED,
    CART_CLEARED,
    CART_CHECKOUT
}
