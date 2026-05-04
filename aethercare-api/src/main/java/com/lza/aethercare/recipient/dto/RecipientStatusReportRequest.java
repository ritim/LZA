package com.lza.aethercare.recipient.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Spec § Master §7：被照顧者主動回報「身體不舒服」(FEELING_UNWELL)。
 *
 * <p>由 backend 建 FEELING_UNWELL care event，risk MEDIUM；若日後 UI 接危險徵兆 quick-question，
 * 可在 metadata 補 hasChestPain / hasNeuroSymptoms 等 flag，由 risk classifier 升級到 HIGH。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientStatusReportRequest {

    @Positive
    private Long careRecipientId;

    @Size(max = 500)
    private String symptom;

    /** UI quick-questions 結果，例如 {hasChestPain: true}；序列化進 care_event.metadata。 */
    private java.util.Map<String, Object> dangerSignals;

    private OffsetDateTime occurredAt;
}
