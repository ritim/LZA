package com.lza.aethercare.action.dto;

import com.lza.aethercare.action.enums.CareActionType;

import java.time.OffsetDateTime;

/** 回填動作 response：建立後返回 action 識別資訊。 */
public record CareActionResponse(
        Long actionId,
        Long taskId,
        Long workflowId,
        Long actorId,
        CareActionType actionType,
        OffsetDateTime createdAt
) {
}
