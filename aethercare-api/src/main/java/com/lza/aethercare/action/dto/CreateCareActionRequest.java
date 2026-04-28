package com.lza.aethercare.action.dto;

import com.lza.aethercare.action.enums.CareActionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 回填動作 request：對應 POST /api/v1/care-tasks/{id}/actions。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCareActionRequest {

    @NotNull
    private Long actorId;

    @NotNull
    private CareActionType actionType;

    private String note;
}
