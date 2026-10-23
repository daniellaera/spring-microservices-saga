package com.daniellaera.inventoryservice.adapter.in.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs the invalidator inside real Spring transactions (driven by a transaction manager with no resource) to
 * prove that the product list is evicted after the commit, and kept after a rollback.
 */
class ProductListCacheInvalidatorTest {

    private final CacheManager cacheManager = new ConcurrentMapCacheManager(CachedProductCatalog.CACHE);
    private final ProductListCacheInvalidator invalidator = new ProductListCacheInvalidator(cacheManager);
    private final TransactionTemplate transaction = new TransactionTemplate(new NoResourceTransactionManager());
    private final Cache productList = cacheManager.getCache(CachedProductCatalog.CACHE);

    @BeforeEach
    void cacheTheList() {
        productList.put(SimpleKey.EMPTY, "the cached list");
    }

    @Test
    void insideTransaction_evictsOnlyAfterCommit() {
        transaction.executeWithoutResult(status -> {
            invalidator.invalidate();
            assertThat(productList.get(SimpleKey.EMPTY)).isNotNull();
        });

        assertThat(productList.get(SimpleKey.EMPTY)).isNull();
    }

    @Test
    void insideTransaction_rollback_keepsTheList() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            invalidator.invalidate();
            throw new IllegalStateException("reservation insert failed");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(productList.get(SimpleKey.EMPTY)).isNotNull();
    }

    @Test
    void outsideTransaction_evictsImmediately() {
        invalidator.invalidate();

        assertThat(productList.get(SimpleKey.EMPTY)).isNull();
    }

    @Test
    void failingCache_isSwallowed() {
        CacheManager failing = mock(CacheManager.class);
        when(failing.getCache(CachedProductCatalog.CACHE)).thenThrow(new IllegalStateException("redis down"));
        ProductListCacheInvalidator withFailingCache = new ProductListCacheInvalidator(failing);

        assertThatCode(() -> transaction.executeWithoutResult(status -> withFailingCache.invalidate()))
                .doesNotThrowAnyException();
    }

    private static class NoResourceTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
