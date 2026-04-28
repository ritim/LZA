package com.lza.aethercare.event.dto;

import com.lza.aethercare.event.enums.CareEventSource;
import com.lza.aethercare.event.enums.CareEventType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.Map;

/** 建立照護事件 request：對應 POST /api/v1/care-events。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCareEventRequest {

    @NotNull
    private Long elderId;

    @NotNull
    private CareEventSource source;

    @NotNull
    private CareEventType eventType;

    @NotNull
    private OffsetDateTime occurredAt;

    private Map<String, Object> metadata;
}
