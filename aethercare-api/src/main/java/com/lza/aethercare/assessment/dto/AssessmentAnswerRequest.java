package com.lza.aethercare.assessment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** 提交評估答案 request：caregiver 一次送出多筆 answers。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessmentAnswerRequest {

    @NotNull
    private Long eventId;

    /** 對應的 task id（可選，例如 caregiver 由通知 deeplink 進入時帶上）。 */
    private Long taskId;

    @NotEmpty
    @Valid
    private List<AnswerItem> answers;
}
