package com.daniellaera.audittrailservice.controller;

import com.daniellaera.audittrailservice.dto.AuditEventDTO;
import com.daniellaera.audittrailservice.dto.AuditEventFilter;
import com.daniellaera.audittrailservice.enums.AuditEventType;
import com.daniellaera.audittrailservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

// Named path variables: the build does not compile with -parameters
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditService auditService;

    private static final int MAX_PAGE_SIZE = 200;

    // Every filter is optional, so a plain GET /audit?page=&size= answers as it always has. An unknown event type or
    // an unreadable instant is a 400.
    @GetMapping
    public ResponseEntity<Page<AuditEventDTO>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "eventType", required = false) List<AuditEventType> eventTypes,
            @RequestParam(name = "serviceName", required = false) String serviceName,
            @RequestParam(name = "userEmail", required = false) String userEmail,
            @RequestParam(name = "orderId", required = false) String orderId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        AuditEventFilter filter = new AuditEventFilter(q, eventTypes, serviceName, userEmail, orderId, from, to);
        return ResponseEntity.ok(auditService.search(filter, Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE)));
    }

    // One order's saga, oldest first: ORDER_CREATED, STOCK_DEDUCTED, PAYMENT_*, ORDER_* or the compensation
    @GetMapping("/orders/{orderId:\\d+}/timeline")
    public ResponseEntity<List<AuditEventDTO>> getTimeline(@PathVariable("orderId") String orderId) {
        return ResponseEntity.ok(auditService.getTimeline(orderId));
    }

    @GetMapping("/user/{email}")
    public ResponseEntity<List<AuditEventDTO>> getByUser(@PathVariable("email") String email) {
        return ResponseEntity.ok(auditService.getByUser(email));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<AuditEventDTO>> getByOrder(@PathVariable("orderId") String orderId) {
        return ResponseEntity.ok(auditService.getByOrder(orderId));
    }
}
