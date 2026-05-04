package com.lza.aethercare.decision.service;

import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.workflow.enums.CareWorkflowType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 規則式風險分類器：將事件型別映射到風險等級與流程類型。 */
@Component
@Slf4j
public class RuleBasedRiskClassifier {

    // Spec § Master §5 風險表 + §9 SLA 表：POSSIBLE_FALL / SOS / FALL_DETECTED 為 HIGH；
    // MISSED_CHECK_IN / NO_RESPONSE / FEELING_UNWELL / NO_ACTIVITY 為 MEDIUM。
    private static final Map<CareEventType, RiskLevel> RISK_MAP = Map.ofEntries(
            Map.entry(CareEventType.FALL_DETECTED, RiskLevel.HIGH),
            Map.entry(CareEventType.POSSIBLE_FALL, RiskLevel.HIGH),
            Map.entry(CareEventType.SOS, RiskLevel.HIGH),
            Map.entry(CareEventType.NO_ACTIVITY, RiskLevel.MEDIUM),
            Map.entry(CareEventType.DAILY_REMINDER, RiskLevel.LOW),
            Map.entry(CareEventType.ACTIVITY_ANOMALY, RiskLevel.MEDIUM),
            Map.entry(CareEventType.MISSED_CHECK_IN, RiskLevel.MEDIUM),
            Map.entry(CareEventType.NO_RESPONSE, RiskLevel.MEDIUM),
            Map.entry(CareEventType.FEELING_UNWELL, RiskLevel.MEDIUM)
    );

    // FEELING_UNWELL / MISSED_CHECK_IN / NO_RESPONSE 沒有專屬 workflow template，
    // 套用 INACTIVITY_CHECK 是現有最近語義（被動觀察 + 升級鏈）。POSSIBLE_FALL 走 FALL_RESPONSE。
    private static final Map<CareEventType, CareWorkflowType> WORKFLOW_MAP = Map.ofEntries(
            Map.entry(CareEventType.FALL_DETECTED, CareWorkflowType.FALL_RESPONSE),
            Map.entry(CareEventType.POSSIBLE_FALL, CareWorkflowType.FALL_RESPONSE),
            Map.entry(CareEventType.SOS, CareWorkflowType.FALL_RESPONSE),
            Map.entry(CareEventType.NO_ACTIVITY, CareWorkflowType.INACTIVITY_CHECK),
            Map.entry(CareEventType.DAILY_REMINDER, CareWorkflowType.REMINDER),
            Map.entry(CareEventType.ACTIVITY_ANOMALY, CareWorkflowType.INACTIVITY_CHECK),
            Map.entry(CareEventType.MISSED_CHECK_IN, CareWorkflowType.INACTIVITY_CHECK),
            Map.entry(CareEventType.NO_RESPONSE, CareWorkflowType.INACTIVITY_CHECK),
            Map.entry(CareEventType.FEELING_UNWELL, CareWorkflowType.INACTIVITY_CHECK)
    );

    public RiskLevel classifyRisk(CareEventType type) {
        RiskLevel level = RISK_MAP.get(type);
        if (level == null) {
            log.warn("未對應的事件型別 type={}，拒絕分類以避免醫療場景誤判為 LOW", type);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "未支援的事件類型: " + type);
        }
        return level;
    }

    public CareWorkflowType resolveWorkflowType(CareEventType type) {
        CareWorkflowType workflowType = WORKFLOW_MAP.get(type);
        if (workflowType == null) {
            log.warn("未對應的事件型別 type={}，拒絕解析 workflow", type);
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "未支援的事件類型: " + type);
        }
        return workflowType;
    }
}
