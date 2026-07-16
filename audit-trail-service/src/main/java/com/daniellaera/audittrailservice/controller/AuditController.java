package com.daniellaera.audittrailservice.controller;

import com.daniellaera.audittrailservice.dto.AuditEventDTO;
import com.daniellaera.audittrailservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
@Slf4j
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ResponseEntity<Page<AuditEventDTO>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(auditService.getAll(PageRequest.of(page, size)));
    }

    @GetMapping("/user/{email}")
    public ResponseEntity<List<AuditEventDTO>> getByUser(@PathVariable String email) {
        return ResponseEntity.ok(auditService.getByUser(email));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<List<AuditEventDTO>> getByOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(auditService.getByOrder(orderId));
    }
}
