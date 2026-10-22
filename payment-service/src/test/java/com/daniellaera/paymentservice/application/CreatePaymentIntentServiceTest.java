package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.application.fakes.EffectLog;
import com.daniellaera.paymentservice.application.fakes.FakePaymentGateway;
import com.daniellaera.paymentservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.paymentservice.domain.model.PaymentGatewayException;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import com.daniellaera.paymentservice.domain.port.in.CreatedPaymentIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ports the createPaymentIntent scenarios of the former StripePaymentServiceTest. */
class CreatePaymentIntentServiceTest {

    private final FakePaymentGateway gateway = new FakePaymentGateway();
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail(new EffectLog());
    private final CreatePaymentIntentService service = new CreatePaymentIntentService(gateway, auditTrail);

    private final CreatePaymentIntentCommand command =
            new CreatePaymentIntentCommand(1999L, "usd", "MacBook Pro", "user@example.com");

    @Test
    void createIntent_asksTheGatewayWithThePurchaseDescription() {
        service.createIntent(command);

        assertThat(gateway.intentRequests()).containsExactly(
                new FakePaymentGateway.IntentRequest(1999L, "usd", "Purchase: MacBook Pro"));
    }

    @Test
    void createIntent_returnsTheIntentAndAuditsTheRequest() {
        CreatedPaymentIntent intent = service.createIntent(command);

        assertThat(intent).isEqualTo(new CreatedPaymentIntent("pi_fake_secret", "pi_fake", "requires_payment_method"));
        assertThat(auditTrail.initiated()).containsExactly(new RecordingAuditTrail.Initiated("pi_fake", command));
    }

    @Test
    void gatewayFailure_propagatesWithoutAudit() {
        gateway.failIntentCreation();

        assertThatThrownBy(() -> service.createIntent(command))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("card declined");
        assertThat(auditTrail.initiated()).isEmpty();
    }
}
