package com.daniellaera.orderservice.adapter.out.messaging;

import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.errors.RecordTooLargeException;

/** Kafka did not acknowledge an order event: it failed, timed out or could not even be handed to the producer. */
public class OrderEventNotSentException extends RuntimeException {

    public OrderEventNotSentException(Long orderId, Throwable cause) {
        super("Order event for orderId " + orderId + " was not sent: " + rootMessage(cause), cause);
    }

    /**
     * Kafka refused this very record (too large, invalid): sending it again fails the same way. Anything else, a
     * timeout, an unreachable broker or a cluster-wide refusal such as an authorization error, is not the record's
     * fault, and is worth retrying for as long as it takes.
     */
    public boolean permanent() {
        for (Throwable t = getCause(); t != null; t = t.getCause()) {
            if (t instanceof RecordTooLargeException || t instanceof InvalidRecordException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + (root.getMessage() != null ? " " + root.getMessage() : "");
    }
}
