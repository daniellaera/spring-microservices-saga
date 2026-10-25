package com.daniellaera.audittrailservice.config;

import com.daniellaera.audittrailservice.application.AuditEventQueryService;
import com.daniellaera.audittrailservice.application.RecordAuditEventService;
import com.daniellaera.audittrailservice.domain.port.out.AuditEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The use cases are plain classes: one bean each. */
@Configuration
public class AuditUseCaseConfig {

    @Bean
    RecordAuditEventService recordAuditEventService(AuditEventRepository repository) {
        return new RecordAuditEventService(repository);
    }

    @Bean
    AuditEventQueryService auditEventQueryService(AuditEventRepository repository) {
        return new AuditEventQueryService(repository);
    }
}
