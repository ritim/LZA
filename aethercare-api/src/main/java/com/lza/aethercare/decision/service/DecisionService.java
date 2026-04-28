package com.lza.aethercare.decision.service;

import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.workflow.enums.CareWorkflowType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 決策服務 facade：純 in-memory，無 transaction。 */
@Service
@RequiredArgsConstructor
public class DecisionService {

    private final RuleBasedRiskClassifier classifier;

    public RiskLevel classify(CareEventType eventType) {
        return classifier.classifyRisk(eventType);
    }

    public CareWorkflowType resolveWorkflowType(CareEventType eventType) {
        return classifier.resolveWorkflowType(eventType);
    }
}
