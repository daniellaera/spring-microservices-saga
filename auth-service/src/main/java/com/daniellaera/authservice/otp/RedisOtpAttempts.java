package com.daniellaera.authservice.otp;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class RedisOtpAttempts implements OtpAttempts {

    private static final String FAILURES_PREFIX = "otp-failures:";
    private static final String ISSUED_PREFIX = "otp-issued:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public long recordFailure(String email, Duration ttl) {
        return increment(FAILURES_PREFIX + email, ttl);
    }

    @Override
    public long recordIssued(String email, Duration window) {
        return increment(ISSUED_PREFIX + email, window);
    }

    @Override
    public void resetFailures(String email) {
        redisTemplate.delete(FAILURES_PREFIX + email);
    }

    // the first increment starts the expiry, later ones keep it: a fixed window, not a sliding one
    private long increment(String key, Duration ttl) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, ttl);
        }
        return count == null ? 0 : count;
    }
}
