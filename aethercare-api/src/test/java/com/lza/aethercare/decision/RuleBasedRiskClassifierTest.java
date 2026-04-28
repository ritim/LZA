package com.lza.aethercare.decision;

import com.lza.aethercare.decision.service.RuleBasedRiskClassifier;
import com.lza.aethercare.event.enums.CareEventType;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.workflow.enums.CareWorkflowType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 規則式風險分類器單元測試：驗證事件型別對映至風險等級與流程類型的正確性。
 */
@ExtendWith(MockitoExtension.class)
class RuleBasedRiskClassifierTest {

    private final RuleBasedRiskClassifier classifier = new RuleBasedRiskClassifier();

    /** 驗證 FALL_DETECTED 被分類為 HIGH 風險。 */
    @Test
    void should_classify_FALL_DETECTED_as_HIGH() {
        RiskLevel result = classifier.classifyRisk(CareEventType.FALL_DETECTED);
        assertThat(result).isEqualTo(RiskLevel.HIGH);
    }

    /** 驗證 SOS 被分類為 HIGH 風險。 */
    @Test
    void should_classify_SOS_as_HIGH() {
        RiskLevel result = classifier.classifyRisk(CareEventType.SOS);
        assertThat(result).isEqualTo(RiskLevel.HIGH);
    }

    /** 驗證 NO_ACTIVITY 被分類為 MEDIUM 風險。 */
    @Test
    void should_classify_NO_ACTIVITY_as_MEDIUM() {
        RiskLevel result = classifier.classifyRisk(CareEventType.NO_ACTIVITY);
        assertThat(result).isEqualTo(RiskLevel.MEDIUM);
    }

    /** 驗證 DAILY_REMINDER 被分類為 LOW 風險。 */
    @Test
    void should_classify_DAILY_REMINDER_as_LOW() {
        RiskLevel result = classifier.classifyRisk(CareEventType.DAILY_REMINDER);
        assertThat(result).isEqualTo(RiskLevel.LOW);
    }

    /** 驗證 FALL_DETECTED 對映到 FALL_RESPONSE 流程。 */
    @Test
    void should_resolve_FALL_DETECTED_to_FALL_RESPONSE_workflow() {
        CareWorkflowType result = classifier.resolveWorkflowType(CareEventType.FALL_DETECTED);
        assertThat(result).isEqualTo(CareWorkflowType.FALL_RESPONSE);
    }

    /** 驗證 SOS 對映到 FALL_RESPONSE 流程。 */
    @Test
    void should_resolve_SOS_to_FALL_RESPONSE_workflow() {
        CareWorkflowType result = classifier.resolveWorkflowType(CareEventType.SOS);
        assertThat(result).isEqualTo(CareWorkflowType.FALL_RESPONSE);
    }

    /** 驗證 NO_ACTIVITY 對映到 INACTIVITY_CHECK 流程。 */
    @Test
    void should_resolve_NO_ACTIVITY_to_INACTIVITY_CHECK_workflow() {
        CareWorkflowType result = classifier.resolveWorkflowType(CareEventType.NO_ACTIVITY);
        assertThat(result).isEqualTo(CareWorkflowType.INACTIVITY_CHECK);
    }

    /** 驗證 DAILY_REMINDER 對映到 REMINDER 流程。 */
    @Test
    void should_resolve_DAILY_REMINDER_to_REMINDER_workflow() {
        CareWorkflowType result = classifier.resolveWorkflowType(CareEventType.DAILY_REMINDER);
        assertThat(result).isEqualTo(CareWorkflowType.REMINDER);
    }
}
