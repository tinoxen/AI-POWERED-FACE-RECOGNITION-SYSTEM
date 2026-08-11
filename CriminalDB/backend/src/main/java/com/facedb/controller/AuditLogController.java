package com.facedb.controller;

import com.facedb.model.AuditLog;
import com.facedb.repository.AuditLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // Access restricted to ROLE_ADMIN via SecurityConfig.
    @GetMapping
    public List<AuditLog> list() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }
}
