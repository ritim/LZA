package com.lza.aethercare.recipient.dto;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Spec § Master §7：被照顧者 CHECK_IN 上報。
 *
 * <p>{@code careRecipientId} 從 {@code X-Care-Recipient-Id} header 解析；
 * 若 header 缺漏才從 body 取（demo 友善）。{@code occurredAt} 可省，server 端用
 * {@code Clock.now()} 補。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipientCheckInRequest {

    @Positive
    private Long careRecipientId;

    private OffsetDateTime occurredAt;

    private String note;
}
