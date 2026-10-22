package com.daniellaera.inventoryservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * @EnableCaching on the application class needs a CacheManager, which test slices (@JsonTest, @DataJpaTest)
 * do not configure.
 */
@TestConfiguration(proxyBeanMethods = false)
public class NoCachingConfiguration {

    @Bean
    public CacheManager cacheManager() {
        return new NoOpCacheManager();
    }
}
