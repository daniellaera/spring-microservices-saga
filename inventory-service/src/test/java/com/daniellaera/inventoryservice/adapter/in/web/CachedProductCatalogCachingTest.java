package com.daniellaera.inventoryservice.adapter.in.web;

import com.daniellaera.inventoryservice.adapter.in.web.dto.ProductRequest;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.ProductAlreadyExistsException;
import com.daniellaera.inventoryservice.domain.port.in.ManageProductsUseCase;
import com.daniellaera.inventoryservice.domain.port.in.ProductQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/** Runs the catalog behind Spring's caching proxy to pin what is cached and what evicts the cache. */
@SpringJUnitConfig(CachedProductCatalogCachingTest.Config.class)
class CachedProductCatalogCachingTest {

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        ProductQuery productQuery() {
            return mock(ProductQuery.class);
        }

        @Bean
        ManageProductsUseCase manageProductsUseCase() {
            return mock(ManageProductsUseCase.class);
        }

        @Bean
        CachedProductCatalog cachedProductCatalog(ProductQuery productQuery,
                                                  ManageProductsUseCase manageProductsUseCase) {
            return new CachedProductCatalog(productQuery, manageProductsUseCase);
        }
    }

    private static final Product MACBOOK = Product.restore(1L, "MacBook Pro", 10, new BigDecimal("1299.99"), null);

    @Autowired
    private CachedProductCatalog catalog;

    @Autowired
    private ProductQuery productQuery;

    @Autowired
    private ManageProductsUseCase manageProductsUseCase;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        reset(productQuery, manageProductsUseCase);
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        when(productQuery.getAllProducts()).thenReturn(List.of(MACBOOK));
        when(productQuery.getProductById(anyLong())).thenReturn(MACBOOK);
        when(manageProductsUseCase.createProduct(any(), anyInt(), any())).thenReturn(MACBOOK);
        when(manageProductsUseCase.restock(anyLong(), anyInt())).thenReturn(MACBOOK);
    }

    @Test
    void productList_isCachedUnderProductsV2() {
        catalog.getAllProducts();
        catalog.getAllProducts();

        verify(productQuery, times(1)).getAllProducts();
        assertThat(cacheManager.getCacheNames()).containsExactly("products-v2");
        assertThat(cacheManager.getCache("products-v2").get(SimpleKey.EMPTY)).isNotNull();
    }

    @Test
    void emptyList_isNotCached() {
        when(productQuery.getAllProducts()).thenReturn(List.of());

        catalog.getAllProducts();
        catalog.getAllProducts();

        verify(productQuery, times(2)).getAllProducts();
    }

    @Test
    void createProduct_evictsTheList() {
        catalog.getAllProducts();

        catalog.createProduct(new ProductRequest("iPhone 16", 5, new BigDecimal("999.99")));
        catalog.getAllProducts();

        verify(productQuery, times(2)).getAllProducts();
    }

    @Test
    void failedCreate_keepsTheList() {
        when(manageProductsUseCase.createProduct(any(), anyInt(), any()))
                .thenThrow(new ProductAlreadyExistsException("MacBook Pro"));
        catalog.getAllProducts();

        assertThatThrownBy(() -> catalog.createProduct(new ProductRequest("MacBook Pro", 5, BigDecimal.ONE)))
                .isInstanceOf(ProductAlreadyExistsException.class);
        catalog.getAllProducts();

        verify(productQuery, times(1)).getAllProducts();
    }

    @Test
    void restock_evictsTheList() {
        catalog.getAllProducts();

        catalog.restock(1L, 5);
        catalog.getAllProducts();

        verify(productQuery, times(2)).getAllProducts();
    }

    @Test
    void getProductById_isNotCachedAndKeepsTheList() {
        catalog.getAllProducts();

        catalog.getProductById(1L);
        catalog.getProductById(1L);
        catalog.getAllProducts();

        verify(productQuery, times(2)).getProductById(1L);
        verify(productQuery, times(1)).getAllProducts();
    }
}
