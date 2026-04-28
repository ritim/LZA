package com.lza.aethercare.event.entity;

import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventStatus;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
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
import org.hibernate.annotations.ColumnTransformer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** 照護事件：sensor / app 上報的原始事件資料。 */
@Entity
@Table(name = "care_event")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private CareEventSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private CareEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CareEventStatus status;

    /** PG JSONB 欄位；以 String JSON 存放，寫入時用 ::jsonb cast。 */
    @Column(name = "metadata", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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
        if (!(o instanceof CareEvent that)) return false;
        if (id == null || that.id == null) return super.equals(o);
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? super.hashCode() : Objects.hash(id);
    }
}
