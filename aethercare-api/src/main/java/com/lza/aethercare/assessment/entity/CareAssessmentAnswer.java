package com.lza.aethercare.assessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** Caregiver 評估答案：append-only 紀錄每筆問題-答案 + danger flag。 */
@Entity
@Table(name = "care_assessment_answer")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareAssessmentAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所屬 tenant；service 層寫入時從 TenantContext 帶入。 */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "caregiver_id", nullable = false)
    private Long caregiverId;

    @Column(name = "question_id", nullable = false, length = 100)
    private String questionId;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "answer", nullable = false, length = 255)
    private String answer;

    @Column(name = "danger_detected", nullable = false)
    private boolean dangerDetected;

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
        if (!(o instanceof CareAssessmentAnswer that)) return false;
        if (id == null || that.id == null) return super.equals(o);
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? super.hashCode() : Objects.hash(id);
    }
}
