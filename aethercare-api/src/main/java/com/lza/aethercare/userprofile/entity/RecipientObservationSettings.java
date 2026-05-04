package com.lza.aethercare.userprofile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Filter;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Spec § Master §0 / Gap C：被照顧者觀察設定。
 *
 * <p>{@code care_recipient_id} 對應 elder_profile.id（沒有 FK 以利日後 rename）。
 * {@code escalation_policy_json} 預留 JSONB 欄位，MVP 不在此 entity 序列化，由 service 層存取。
 */
@Entity
@Table(name = "recipient_observation_settings")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientObservationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "care_recipient_id", nullable = false, unique = true)
    private Long careRecipientId;

    @Column(name = "expected_checkin_time")
    private LocalTime expectedCheckinTime;

    @Column(name = "checkin_grace_minutes", nullable = false)
    private Integer checkinGraceMinutes;

    @Column(name = "max_inactive_minutes_daytime", nullable = false)
    private Integer maxInactiveMinutesDaytime;

    @Column(name = "max_inactive_minutes_night", nullable = false)
    private Integer maxInactiveMinutesNight;

    @Column(name = "passive_monitoring_enabled", nullable = false)
    private Boolean passiveMonitoringEnabled;

    @Column(name = "escalation_policy_json", columnDefinition = "jsonb")
    private String escalationPolicyJson;

    @Column(name = "created_at", nullable = false)
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
}
