package com.lza.aethercare.action.dto;

import com.lza.aethercare.action.enums.CareActionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Spec §6.7 POST /api/workflows/{id}/actions 請求 body。
 *
 * <p>本端點為 spec 對齊用，內部 delegate 到 task 層級的
 * {@link CareActionService#handle(Long, Long, CreateCareActionRequest)}；taskId 必填。
 */
@Data
public class WorkflowActionRequest {

    /** Spec 要求帶 eventId 用於審計；目前由 task 反查，欄位接受但暫不依賴。 */
    private Long eventId;

    @NotNull(message = "taskId 必填")
    private Long taskId;

    @NotNull(message = "actionType 必填")
    private CareActionType actionType;

    private String note;
}
