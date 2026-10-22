package com.daniellaera.inventoryservice.config;

import com.daniellaera.inventoryservice.application.CompensateStockService;
import com.daniellaera.inventoryservice.application.ManageProductsService;
import com.daniellaera.inventoryservice.application.ProductQueryService;
import com.daniellaera.inventoryservice.application.ReserveStockService;
import com.daniellaera.inventoryservice.application.StockReserver;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;
import com.daniellaera.inventoryservice.domain.port.out.CompensationLedger;
import com.daniellaera.inventoryservice.domain.port.out.InventoryResultPublisher;
import com.daniellaera.inventoryservice.domain.port.out.ProductRepository;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InventoryUseCaseConfig {

    @Bean
    public StockReserver stockReserver(ProductRepository productRepository,
                                       StockReservationRepository stockReservationRepository,
                                       AuditTrail auditTrail) {
        return new StockReserver(productRepository, stockReservationRepository, auditTrail);
    }

    @Bean
    public ReserveStockService reserveStockService(StockReservationRepository stockReservationRepository,
                                                   StockReserver stockReserver,
                                                   InventoryResultPublisher inventoryResultPublisher) {
        return new ReserveStockService(stockReservationRepository, stockReserver, inventoryResultPublisher);
    }

    @Bean
    public CompensateStockService compensateStockService(ProductRepository productRepository,
                                                         StockReservationRepository stockReservationRepository,
                                                         CompensationLedger compensationLedger,
                                                         AuditTrail auditTrail) {
        return new CompensateStockService(productRepository, stockReservationRepository, compensationLedger,
                auditTrail);
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
