package com.lza.aethercare.task.entity;

import com.lza.aethercare.task.enums.AssigneeType;
import com.lza.aethercare.task.enums.CareTaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** 照護任務：對應 workflow 內某層級派發的任務。 */
@Entity
@Table(name = "care_task")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignee_type", nullable = false)
    private AssigneeType assigneeType;

    @Column(name = "level", nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CareTaskStatus status;

    @Column(name = "deadline_at", nullable = false)
    private OffsetDateTime deadlineAt;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CareTask that)) return false;
        if (id == null || that.id == null) return super.equals(o);
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? super.hashCode() : Objects.hash(id);
    }
}
