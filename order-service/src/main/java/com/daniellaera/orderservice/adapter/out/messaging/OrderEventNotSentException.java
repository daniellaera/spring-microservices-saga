package com.daniellaera.orderservice.adapter.out.messaging;

/** Kafka did not acknowledge an order event: it failed, timed out or could not even be handed to the producer. */
public class OrderEventNotSentException extends RuntimeException {

    public OrderEventNotSentException(Long orderId, Throwable cause) {
        super("Order event for orderId " + orderId + " was not sent: " + rootMessage(cause), cause);
    }

    private static String rootMessage(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() != null ? " " + root.getMessage() : "");
    }
}
