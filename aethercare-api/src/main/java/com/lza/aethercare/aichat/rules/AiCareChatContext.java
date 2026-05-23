package com.lza.aethercare.aichat.rules;

import com.lza.aethercare.ai.knowledge.CareKnowledge;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;

import java.util.List;
import java.util.Optional;

/**
 * Spec § AI_Care_Chat §5：rules engine 的輸入 context。
 *
 * <p>故意以 record 包成 immutable，避免 service 內部一邊算一邊被改。空值情境（task 已結案）
 * 用 Optional 表達。
 */
public record AiCareChatContext(
        CareEvent event,
        CareWorkflowInstance workflow,
        Optional<CareTask> currentTask,
        Optional<CareKnowledge> knowledge,
        /** 該 workflow 上既有的 caregiver action types（小寫字串清單，方便 contains 檢查）。 */
        List<String> priorActionTypes,
        /** Caregiver 輸入文字；可為 null（first-message 場景）。 */
        String message,
        /** 被照顧者顯示名稱；缺值時模板回退為「長者」。 */
        Optional<String> recipientName
) {
    /** Legacy 6-arg constructor，預設 recipientName 為空。讓既有測試/呼叫端不必逐處改。 */
    public AiCareChatContext(CareEvent event, CareWorkflowInstance workflow,
                             Optional<CareTask> currentTask, Optional<CareKnowledge> knowledge,
                             List<String> priorActionTypes, String message) {
        this(event, workflow, currentTask, knowledge, priorActionTypes, message, Optional.empty());
    }
}
