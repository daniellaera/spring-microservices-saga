package com.daniellaera.authservice.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RedisOtpRepository implements OtpRepository {

    private final StringRedisTemplate redisTemplate;
    private static final String OTP_PREFIX = "otp:";

    @Override
    public void save(String email, OtpCode otp, Duration ttl) {
        redisTemplate.opsForValue().set(OTP_PREFIX + email, otp.value(), ttl);
        log.info("=== OTP stored for {} TTL={}s", email, ttl.getSeconds());
    }

    @Override
    public Optional<String> find(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(OTP_PREFIX + email));
    }

    @Override
    public void delete(String email) {
        redisTemplate.delete(OTP_PREFIX + email);
    }
}
