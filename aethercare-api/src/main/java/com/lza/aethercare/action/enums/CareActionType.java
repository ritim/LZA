package com.lza.aethercare.action.enums;

/**
 * 使用者回填動作類型。
 *
 * <p>內部 3 種：CONFIRM_SAFE / NEED_HELP / ACKNOWLEDGE。
 *
 * <p>Spec §6.7 要求多種對外 action（語意對應到內部 3 種；service 層作映射），保留為合法 enum
 * 以避免前端送出 spec 名稱被視為 INVALID_REQUEST：
 * <ul>
 *   <li>升級族（→ NEED_HELP）：{@link #CALL_EMERGENCY} / {@link #ESCALATE}
 *       / {@link #CALL_SECOND_CONTACT} / {@link #REQUEST_HELP}</li>
 *   <li>記錄不結案族（→ ACKNOWLEDGE，spec § Gap D）：{@link #CALL_ELDER} / {@link #ADD_NOTE}
 *       / {@link #CALL_NO_ANSWER} / {@link #MARK_UNABLE_TO_CONFIRM}</li>
 * </ul>
 */
public enum CareActionType {
    CONFIRM_SAFE,
    NEED_HELP,
    ACKNOWLEDGE,
    // Spec §6.7 alias actions
    CALL_EMERGENCY,
    ESCALATE,
    CALL_ELDER,
    CALL_SECOND_CONTACT,
    REQUEST_HELP,
    MARK_UNABLE_TO_CONFIRM,
    ADD_NOTE,
    /**
     * Spec § Master §6 Flow C / § Gap D：「電話未接」記錄但**不結案**、不升級。
     * service 層映射為 ACKNOWLEDGE 內部語意（記錄、不轉狀態）。
     */
    CALL_NO_ANSWER
}
