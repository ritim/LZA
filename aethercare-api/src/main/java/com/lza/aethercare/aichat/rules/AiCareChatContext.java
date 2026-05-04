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
        String message
) {
}
