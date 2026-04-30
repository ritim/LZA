package com.lza.aethercare.assessment.dto;

/** 提交評估答案 response：含 risk reevaluation 結果與是否成功儲存。 */
public record AssessmentAnswerResponse(
        Long workflowId,
        Long taskId,
        RiskReevaluation riskReevaluation,
        boolean saved
) {
}
