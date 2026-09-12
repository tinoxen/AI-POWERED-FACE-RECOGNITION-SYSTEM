package com.facedb.service;

import com.facedb.model.AuditLog;
import com.facedb.repository.AuditLogRepository;

import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(
            AuditLogRepository auditLogRepository) {

        this.auditLogRepository =
                auditLogRepository;
    }

    public void log(
            String username,
            String action,
            Long targetPersonId,
            String details,
            String ipAddress) {

        AuditLog auditLog =
                new AuditLog(
                        username,
                        action,
                        targetPersonId,
                        details,
                        ipAddress
                );

        auditLogRepository.save(auditLog);
    }
}
