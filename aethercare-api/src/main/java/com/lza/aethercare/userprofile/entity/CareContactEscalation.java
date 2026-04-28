package com.lza.aethercare.userprofile.entity;

import com.lza.aethercare.userprofile.enums.NotificationChannel;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/** 升級聯絡人設定：定義各層級對應通知對象、通道與 SLA。 */
@Entity
@Table(name = "care_contact_escalation")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CareContactEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Column(name = "contact_user_id", nullable = false)
    private Long contactUserId;

    @Column(name = "level", nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "sla_seconds", nullable = false)
    private int slaSeconds;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

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
        if (!(o instanceof CareContactEscalation that)) return false;
        if (id == null || that.id == null) return super.equals(o);
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? super.hashCode() : Objects.hash(id);
    }
}
