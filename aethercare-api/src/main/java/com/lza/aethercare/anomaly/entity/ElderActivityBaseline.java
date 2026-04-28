package com.lza.aethercare.anomaly.entity;

import com.lza.aethercare.anomaly.enums.ActivityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Objects;

/** 活動 baseline：每位長者每種活動每小時的期望分布（mean / stddev / sample count）。 */
@Entity
@Table(name = "elder_activity_baseline",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_baseline_elder_activity_hour",
                columnNames = {"elder_id", "activity_type", "hour_of_day"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ElderActivityBaseline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "elder_id", nullable = false)
    private Long elderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false)
    private ActivityType activityType;

    @Column(name = "hour_of_day", nullable = false)
    private int hourOfDay;

    @Column(name = "expected_count_mean", nullable = false)
    private double expectedCountMean;

    @Column(name = "expected_count_stddev", nullable = false)
    private double expectedCountStddev;

    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ElderActivityBaseline that)) return false;
        if (id == null || that.id == null) return super.equals(o);
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return id == null ? super.hashCode() : Objects.hash(id);
    }
}
