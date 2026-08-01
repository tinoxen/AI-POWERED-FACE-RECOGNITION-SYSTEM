package com.facedb.service;

import com.facedb.model.AuditLog;
import com.facedb.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

/**
 * Writes append-only audit records. Nothing in this codebase should ever
 * update or delete a row in audit_logs - only insert.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String username, String action, Long targetPersonId, String details, String ipAddress) {
        auditLogRepository.save(new AuditLog(username, action, targetPersonId, details, ipAddress));
    }
}
