package com.lza.aethercare.recipient.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Spec § Master §7：被照顧者按下「我需要幫忙」上報 SOS。
 *
 * <p>SOS 一律建立 HIGH care event 並啟動 workflow，沒有 cancel grace（spec §4 標明可在 UI 加，
 * 但 backend 一律啟動 — 後端不可吞掉緊急訊號）。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientSosRequest {

    @Positive
    private Long careRecipientId;

    private OffsetDateTime occurredAt;

    private String note;

    private String location;
}
