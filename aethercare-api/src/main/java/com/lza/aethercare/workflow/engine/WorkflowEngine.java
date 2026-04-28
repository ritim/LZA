package com.lza.aethercare.workflow.engine;

/**
 * Workflow engine 抽象介面（為未來 Temporal migration 預留 hook）。
 *
 * <p>MVP 由 {@link com.lza.aethercare.workflow.service.CareWorkflowService}
 * 透過 {@code @Scheduled} timeout scanner + native conditional update 實作；
 * CareWorkflowService 暫不 implements 此介面（避免大改），Phase 2.x 替換為
 * {@code TemporalWorkflowEngineImpl} 時，把 CareWorkflowService 與 TemporalImpl
 * 都 implements 此介面，由 profile / config 切換 bean（@ConditionalOnProperty
 * {@code aethercare.workflow.engine=temporal|scheduled}）。
 *
 * <p>Migration roadmap 詳見
 * {@code docs/deployment/temporal-migration-roadmap.md}。
 *
 * <p>所有 method 皆為冪等（idempotent）：重複呼叫同一 workflowId / eventId
 * 不會產生額外副作用，符合 Temporal activity 的 retry 假設。
 */
public interface WorkflowEngine {

    /**
     * 為指定事件啟動新的 workflow，回傳 workflow id。
     *
     * @param eventId 對應 care_event.id
     * @param elderId 長者 id
     * @return 新建立的 workflow id
     */
    Long startWorkflow(Long eventId, Long elderId);

    /**
     * 由家屬 / 照服員確認長者安全，將 workflow 標記為 RESOLVED。
     *
     * @param workflowId workflow 識別碼
     * @param actorId    執行者 user id（可為 null = 系統觸發）
     */
    void confirmSafe(Long workflowId, Long actorId);

    /**
     * 升級 workflow 至下一層聯絡人；若已無下一層則改呼叫
     * {@link #markUnresolved(Long, Long, String)}。
     *
     * @param workflowId workflow 識別碼
     * @param actorId    執行者 user id（可為 null = scheduler 觸發）
     */
    void escalate(Long workflowId, Long actorId);

    /**
     * 將 workflow 標記為 UNRESOLVED 並記錄原因（耗盡聯絡人 / 強制結案）。
     *
     * @param workflowId workflow 識別碼
     * @param actorId    執行者 user id（可為 null = scheduler 觸發）
     * @param reason     人類可讀理由，會寫入 audit log
     */
    void markUnresolved(Long workflowId, Long actorId, String reason);
}
