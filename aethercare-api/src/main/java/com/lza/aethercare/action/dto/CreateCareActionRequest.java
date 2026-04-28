package com.lza.aethercare.action.dto;

import com.lza.aethercare.action.enums.CareActionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 回填動作 request：對應 POST /api/v1/care-tasks/{id}/actions。
 * actorId 改由 SecurityContext 取得（JWT），不再由 client 傳入。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCareActionRequest {

    @NotNull
    private CareActionType actionType;

    private String note;
}
