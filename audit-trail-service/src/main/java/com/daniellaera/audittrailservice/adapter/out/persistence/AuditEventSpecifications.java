package com.daniellaera.audittrailservice.adapter.out.persistence;

import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** GET /audit's filters as one query; created_at holds UTC, so the instants are compared at UTC. */
public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEventJpaEntity> matching(AuditEventFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (hasText(filter.q())) {
                predicates.add(search(root, cb, filter.q().trim()));
            }
            if (filter.eventTypes() != null && !filter.eventTypes().isEmpty()) {
                predicates.add(root.get("eventType").in(filter.eventTypes()));
            }
            if (hasText(filter.serviceName())) {
                predicates.add(cb.equal(root.get("serviceName"), filter.serviceName().trim()));
            }
            if (hasText(filter.userEmail())) {
                predicates.add(cb.equal(cb.lower(root.get("userEmail")), filter.userEmail().trim().toLowerCase(Locale.ROOT)));
            }
            if (hasText(filter.orderId())) {
                predicates.add(cb.equal(root.get("correlationId"), filter.orderId().trim()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), LocalDateTime.ofInstant(filter.from(), ZoneOffset.UTC)));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), LocalDateTime.ofInstant(filter.to(), ZoneOffset.UTC)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // The text columns contain q; the event type (stored as its name) is matched on the enum's names instead
    private static Predicate search(Root<AuditEventJpaEntity> root, CriteriaBuilder cb, String q) {
        String pattern = "%" + escapeLike(q.toLowerCase(Locale.ROOT)) + "%";
        List<Predicate> any = new ArrayList<>();
        for (String column : List.of("userEmail", "entityType", "entityId", "serviceName", "correlationId")) {
            Expression<String> value = cb.lower(root.get(column));
            any.add(cb.like(value, pattern, '\\'));
        }
        List<AuditEventType> types = Arrays.stream(AuditEventType.values())
                .filter(type -> type.name().contains(q.toUpperCase(Locale.ROOT)))
                .toList();
        if (!types.isEmpty()) {
            any.add(root.get("eventType").in(types));
        }
        return cb.or(any.toArray(Predicate[]::new));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
