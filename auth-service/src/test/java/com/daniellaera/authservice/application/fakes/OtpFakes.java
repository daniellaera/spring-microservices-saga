package com.daniellaera.authservice.application.fakes;

import com.daniellaera.authservice.domain.model.OtpCode;
import com.daniellaera.authservice.domain.port.out.OtpAttempts;
import com.daniellaera.authservice.domain.port.out.OtpEventPublisher;
import com.daniellaera.authservice.domain.port.out.OtpGenerator;
import com.daniellaera.authservice.domain.port.out.OtpRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The OTP ports in memory: a generator of known codes, the stored codes with their TTL, counters, published codes. */
public final class OtpFakes {

    private OtpFakes() {
    }

    public static class FixedOtpGenerator implements OtpGenerator {
        private final List<String> codes;
        private int next;

        public FixedOtpGenerator(String... codes) {
            this.codes = List.of(codes);
        }

        @Override
        public OtpCode generate() {
            return new OtpCode(codes.get(Math.min(next++, codes.size() - 1)));
        }
    }

    public static class InMemoryOtpRepository implements OtpRepository {
        public final Map<String, String> codes = new HashMap<>();
        public final Map<String, Duration> ttls = new HashMap<>();

        @Override
        public void save(String email, OtpCode otp, Duration ttl) {
            codes.put(email, otp.value());
            ttls.put(email, ttl);
        }

        @Override
        public Optional<String> find(String email) {
            return Optional.ofNullable(codes.get(email));
        }

        @Override
        public void delete(String email) {
            codes.remove(email);
        }
    }

    public static class InMemoryOtpAttempts implements OtpAttempts {
        public final Map<String, Long> failures = new HashMap<>();
        public final Map<String, Long> issued = new HashMap<>();
        public final List<String> windows = new ArrayList<>();

        @Override
        public long recordFailure(String email, Duration ttl) {
            windows.add("failures " + ttl.toMinutes() + " min");
            return failures.merge(email, 1L, Long::sum);
        }

        @Override
        public long recordIssued(String email, Duration window) {
            windows.add("issued " + window.toMinutes() + " min");
            return issued.merge(email, 1L, Long::sum);
        }

        @Override
        public void resetFailures(String email) {
            failures.remove(email);
        }
    }

    public static class RecordingOtpEventPublisher implements OtpEventPublisher {
        public final List<String> published = new ArrayList<>();

        @Override
        public void publishOtpGenerated(String email, OtpCode otp) {
            published.add(email + " " + otp.value());
        }
    }
}
