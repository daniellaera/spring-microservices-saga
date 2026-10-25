package com.daniellaera.inventoryservice.config;

import com.daniellaera.inventoryservice.application.CompensateStockService;
import com.daniellaera.inventoryservice.application.ManageProductsService;
import com.daniellaera.inventoryservice.application.ProductQueryService;
import com.daniellaera.inventoryservice.application.ReportDeadLetteredService;
import com.daniellaera.inventoryservice.application.ReserveStockService;
import com.daniellaera.inventoryservice.application.StockReserver;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;
import com.daniellaera.inventoryservice.domain.port.out.CompensationLedger;
import com.daniellaera.inventoryservice.domain.port.out.InventoryResultPublisher;
import com.daniellaera.inventoryservice.domain.port.out.ProductListCache;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InventoryUseCaseConfig {

    @Bean
    public StockReserver stockReserver(ProductRepository productRepository,
                                       StockReservationRepository stockReservationRepository,
                                       AuditTrail auditTrail, ProductListCache productListCache) {
        return new StockReserver(productRepository, stockReservationRepository, auditTrail, productListCache);
    }

    @Bean
    public ReserveStockService reserveStockService(StockReservationRepository stockReservationRepository,
                                                   StockReserver stockReserver,
                                                   InventoryResultPublisher inventoryResultPublisher,
                                                   @Value("${inventory.reservation.lock-attempts:3}") int lockAttempts) {
        return new ReserveStockService(stockReservationRepository, stockReserver, inventoryResultPublisher,
                lockAttempts);
    }

    @Bean
    public CompensateStockService compensateStockService(ProductRepository productRepository,
                                                         StockReservationRepository stockReservationRepository,
                                                         CompensationLedger compensationLedger,
                                                         AuditTrail auditTrail,
                                                         ProductListCache productListCache) {
        return new CompensateStockService(productRepository, stockReservationRepository, compensationLedger,
                auditTrail, productListCache);
    }

    @Bean
    public ReportDeadLetteredService reportDeadLetteredService(AuditTrail auditTrail) {
        return new ReportDeadLetteredService(auditTrail);
    }

    @Bean
    public ManageProductsService manageProductsService(ProductRepository productRepository, AuditTrail auditTrail) {
        return new ManageProductsService(productRepository, auditTrail);
    }

    @Bean
    public ProductQueryService productQueryService(ProductRepository productRepository) {
        return new ProductQueryService(productRepository);
    }
}
