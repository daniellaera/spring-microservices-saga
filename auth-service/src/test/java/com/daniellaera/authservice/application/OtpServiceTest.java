package com.daniellaera.authservice.application;

import com.daniellaera.authservice.application.fakes.OtpFakes.FixedOtpGenerator;
import com.daniellaera.authservice.application.fakes.OtpFakes.InMemoryOtpAttempts;
import com.daniellaera.authservice.application.fakes.OtpFakes.InMemoryOtpRepository;
import com.daniellaera.authservice.application.fakes.OtpFakes.RecordingOtpEventPublisher;
import com.daniellaera.authservice.domain.model.AuthenticationFailedException;
import com.daniellaera.authservice.domain.model.OtpLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpServiceTest {

    private static final String EMAIL = "john@test.com";

    private final InMemoryOtpRepository repository = new InMemoryOtpRepository();
    private final InMemoryOtpAttempts attempts = new InMemoryOtpAttempts();
    private final RecordingOtpEventPublisher publisher = new RecordingOtpEventPublisher();
    private final OtpService service = new OtpService(new FixedOtpGenerator("123456", "654321"), repository, publisher,
            attempts);

    @Test
    void initiate_generatesStoresForFiveMinutesAndPublishes_andForgetsEarlierWrongCodes() {
        attempts.failures.put(EMAIL, 3L);

        service.initiateOtp(EMAIL);

        assertThat(repository.codes).containsEntry(EMAIL, "123456");
        assertThat(repository.ttls).containsEntry(EMAIL, Duration.ofMinutes(5));
        assertThat(publisher.published).containsExactly(EMAIL + " 123456");
        assertThat(attempts.failures).doesNotContainKey(EMAIL);
        assertThat(attempts.windows).containsExactly("issued 15 min");
    }

    @Test
    void initiate_stillIssuesTheFifthCodeOfTheWindow() {
        attempts.issued.put(EMAIL, 4L);

        service.initiateOtp(EMAIL);

        assertThat(publisher.published).hasSize(1);
    }

    @Test
    void initiate_refusesASixthCodeInTheWindow_withNothingStoredOrSent() {
        attempts.issued.put(EMAIL, 5L);

        assertThatThrownBy(() -> service.initiateOtp(EMAIL))
                .isInstanceOfSatisfying(OtpLimitExceededException.class, e -> {
                    assertThat(e.auditReason()).isEqualTo("OTP_TOO_MANY_CODES_ISSUED");
                    assertThat(e.getMessage()).isEqualTo("Too many login attempts — please try again in 15 minutes");
                });
        assertThat(repository.codes).isEmpty();
        assertThat(publisher.published).isEmpty();
    }

    @Test
    void verify_theRightCode_consumesIt() {
        service.initiateOtp(EMAIL);

        service.verifyOtp(EMAIL, "123456");

        assertThat(repository.codes).doesNotContainKey(EMAIL);
        assertThat(attempts.failures).doesNotContainKey(EMAIL);
    }

    @Test
    void verify_aWrongCode_isRefused_andKeepsTheOtp_untilTheFifth() {
        service.initiateOtp(EMAIL);

        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> service.verifyOtp(EMAIL, "000000"))
                    .isInstanceOf(AuthenticationFailedException.class).hasMessage("Invalid OTP code");
        }

        assertThat(repository.codes).containsEntry(EMAIL, "123456");
        assertThat(attempts.failures).containsEntry(EMAIL, 4L);
        assertThat(attempts.windows).last().isEqualTo("failures 5 min");
    }

    @Test
    void verify_theFifthWrongCode_invalidatesTheOtp() {
        service.initiateOtp(EMAIL);
        attempts.failures.put(EMAIL, 4L);

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "000000"))
                .isInstanceOfSatisfying(OtpLimitExceededException.class, e -> {
                    assertThat(e.auditReason()).isEqualTo("OTP_TOO_MANY_WRONG_CODES");
                    assertThat(e.getMessage()).isEqualTo("Too many invalid codes — please login again");
                });
        assertThat(repository.codes).doesNotContainKey(EMAIL);
        assertThat(attempts.failures).doesNotContainKey(EMAIL);
    }

    @Test
    void verify_withoutAStoredCode_isRefused_withoutCountingAFailure() {
        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "123456"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("OTP expired or not found — please login again");
        assertThat(attempts.failures).isEmpty();
    }
}
