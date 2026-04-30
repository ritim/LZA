package com.lza.aethercare.ai.dto;

/** AI 建議給 caregiver 採取的動作（對應 spec §5.5 suggestedActions）。 */
public record SuggestedActionDto(
        String type,
        String label,
        String priority,
        boolean confirmationRequired
) {
}
