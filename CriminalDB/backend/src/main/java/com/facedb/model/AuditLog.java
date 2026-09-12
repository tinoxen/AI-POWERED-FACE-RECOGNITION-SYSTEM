package com.facedb.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Append-only audit trail. Rows are never updated or deleted by the
 * application layer, only inserted, so this table can act as an
 * immutable record of who accessed or modified what.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    /** e.g. LOGIN, VIEW_PERSON, CREATE_PERSON, UPDATE_PERSON, DELETE_PERSON */
    @Column(nullable = false)
    private String action;

    private Long targetPersonId;

    private String details;

    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public AuditLog() {}

    public AuditLog(String username, String action, Long targetPersonId, String details, String ipAddress) {
        this.username = username;
        this.action = action;
        this.targetPersonId = targetPersonId;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getAction() { return action; }
    public Long getTargetPersonId() { return targetPersonId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
