package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.domain.port.out.ProductListCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts the catalog's product list when an order or a compensation changed stock. It lives next to the cache
 * it evicts, which belongs to this adapter. Inside a transaction it evicts after the commit: a rolled-back change
 * keeps the list, and a request between the eviction and the commit cannot cache the old stock again. A failed
 * eviction is logged and swallowed: it runs after the commit, so throwing would only stop the caller from
 * publishing the inventory result.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductListCacheInvalidator implements ProductListCache {

    private final CacheManager cacheManager;

    @Override
    public void invalidate() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict();
                }
            });
        } else {
            evict();
        }
    }

    private void evict() {
        try {
            Cache cache = cacheManager.getCache(CachedProductCatalog.CACHE);
            if (cache != null) {
                cache.clear();
            }
        } catch (RuntimeException e) {
            log.warn("=== Inventory: could not evict the product list cache: {}", e.getMessage());
        }
    }
}
