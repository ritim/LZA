package com.lza.aethercare.aichat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Spec § AI_Care_Chat §4：POST /api/v1/ai/care-chat 請求 body。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiCareChatRequest {

    @NotNull
    @Positive
    private Long careEventId;

    @NotNull
    @Positive
    private Long workflowId;

    /** 當前 task id；可為 null（workflow 已 RESOLVED 時 task 已完成）。 */
    private Long taskId;

    @NotBlank
    @Size(max = 2000)
    private String message;
}
