package com.lza.aethercare.userprofile.dto;

import java.util.List;

/**
 * Spec § Master §7：GET /api/v1/care-recipients/{careRecipientId}/contacts。
 *
 * <p>{@link ElderContactsResponse} 的對等型別，差別只在 wire-level 欄位名稱：
 * 舊 endpoint 給 {@code elderId}，新 canonical endpoint 給 {@code careRecipientId}。
 */
public record CareRecipientContactsResponse(
        Long careRecipientId,
        List<ElderContactResponse> contacts
) {
}
