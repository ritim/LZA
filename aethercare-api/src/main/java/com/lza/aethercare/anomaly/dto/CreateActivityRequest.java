package com.lza.aethercare.anomaly.dto;

import com.lza.aethercare.anomaly.enums.ActivityType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

/** 上報長者活動 request：對應 POST /api/v1/elders/{elderId}/activities。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateActivityRequest {

    @NotNull
    private ActivityType activityType;

    /** 可為 null：未提供時用 Clock.now() 補。 */
    private OffsetDateTime occurredAt;

    private Integer durationSeconds;

    private Map<String, Object> metadata;
}
