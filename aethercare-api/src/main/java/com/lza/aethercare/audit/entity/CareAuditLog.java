package com.lza.aethercare.audit.entity;

import com.lza.aethercare.audit.enums.CareAuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** 系統 audit log：append-only 完整時序記錄。 */
@Entity
@Table(name = "care_audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id")
    private Long workflowId;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "actor_id")
    private Long actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private CareAuditAction action;

    @Column(name = "message", columnDefinition = "text")
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CareAuditLog that)) return false;
        if (id == null || that.id == null) return super.equals(o);
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? super.hashCode() : Objects.hash(id);
    }
}
