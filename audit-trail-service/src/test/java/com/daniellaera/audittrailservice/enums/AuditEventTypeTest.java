package com.daniellaera.audittrailservice.enums;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards against drift: AuditService drops a message whose type is not an AuditEventType, silently for the
 * producer. This lists every type a service sends to audit-topic; add a producer's new type here and to the enum.
 */
class AuditEventTypeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            // auth-service: DefaultAuthService, RefreshTokenService, AuthController (via AuditPublisher)
            "USER_REGISTER", "USER_LOGIN_INITIATED", "USER_LOGIN", "USER_LOGOUT", "USER_PROFILE_UPDATED",
            "TOKEN_REFRESHED",
            // order-service: adapter/out/messaging/KafkaAuditTrail
            "ORDER_CREATED", "ORDER_CONFIRMED", "ORDER_CANCELLED", "OUTBOX_EVENT_FAILED",
            // payment-service: adapter/out/messaging/KafkaAuditTrail
            "PAYMENT_INITIATED", "PAYMENT_SUCCEEDED", "PAYMENT_FAILED", "PAYMENT_REFUNDED", "PAYMENT_REFUND_FAILED",
            "PAYMENT_DEAD_LETTERED",
            // inventory-service: adapter/out/messaging/TransactionAwareKafkaAuditTrail
            "PRODUCT_CREATED", "PRODUCT_RESTOCKED", "STOCK_DEDUCTED", "STOCK_RESTORED"
    })
    void everyTypeAProducerSends_isStored(String eventType) {
        assertThatCode(() -> AuditEventType.valueOf(eventType)).doesNotThrowAnyException();
    }
}
