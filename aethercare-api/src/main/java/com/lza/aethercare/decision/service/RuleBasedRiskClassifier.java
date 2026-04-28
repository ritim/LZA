package com.lza.aethercare.decision.service;

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

    private static final Map<CareEventType, RiskLevel> RISK_MAP = Map.of(
            CareEventType.FALL_DETECTED, RiskLevel.HIGH,
            CareEventType.SOS, RiskLevel.HIGH,
            CareEventType.NO_ACTIVITY, RiskLevel.MEDIUM,
            CareEventType.DAILY_REMINDER, RiskLevel.LOW
    );

    private static final Map<CareEventType, CareWorkflowType> WORKFLOW_MAP = Map.of(
            CareEventType.FALL_DETECTED, CareWorkflowType.FALL_RESPONSE,
            CareEventType.SOS, CareWorkflowType.FALL_RESPONSE,
            CareEventType.NO_ACTIVITY, CareWorkflowType.INACTIVITY_CHECK,
            CareEventType.DAILY_REMINDER, CareWorkflowType.REMINDER
    );

    public RiskLevel classifyRisk(CareEventType type) {
        RiskLevel level = RISK_MAP.get(type);
        if (level == null) {
            log.warn("未對應的事件型別 type={} 預設為 LOW", type);
            return RiskLevel.LOW;
        }
        return level;
    }

    public CareWorkflowType resolveWorkflowType(CareEventType type) {
        CareWorkflowType workflowType = WORKFLOW_MAP.get(type);
        if (workflowType == null) {
            log.warn("未對應的事件型別 type={} 預設為 REMINDER", type);
            return CareWorkflowType.REMINDER;
        }
        return workflowType;
    }
}
