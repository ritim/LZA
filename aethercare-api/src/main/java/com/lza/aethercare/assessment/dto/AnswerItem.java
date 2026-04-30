package com.lza.aethercare.assessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 單筆 caregiver 答案：question id + 原文 question + answer。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerItem {

    @NotBlank
    private String questionId;

    @NotBlank
    private String question;

    @NotBlank
    private String answer;
}
