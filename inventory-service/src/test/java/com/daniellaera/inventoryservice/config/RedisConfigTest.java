package com.daniellaera.inventoryservice.config;

import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductDTO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The cache rename to products-v2 must not change the TTL or the value serializer. */
class RedisConfigTest {

    private final RedisCacheConfiguration configuration = new RedisConfig().cacheConfiguration();

    @Test
    void entriesExpireAfterTenMinutes() {
        assertThat(configuration.getTtlFunction().getTimeToLive(new Object(), null)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void nullValuesAreNotCached() {
        assertThat(configuration.getAllowCacheNullValues()).isFalse();
    }

    @Test
    void valuesAreJdkSerialized() {
        List<ProductDTO> value = List.of(
                new ProductDTO(1L, "MacBook Pro", 10, new BigDecimal("1299.99"), LocalDateTime.of(2026, 1, 15, 9, 30)));

        ByteBuffer written = configuration.getValueSerializationPair().write(value);
        byte[] bytes = new byte[written.remaining()];
        written.get(bytes);

        assertThat(new JdkSerializationRedisSerializer().deserialize(bytes)).isEqualTo(value);
    }
}
