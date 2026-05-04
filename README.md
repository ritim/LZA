# LZA / AetherCare Documentation Index

本資料夾目前是 LZA 與 AetherCare MVP 的規格工作區。

## 文件使用方式

請先閱讀新的 `docs/` 文件，舊文件保留為設計來源與歷史參考。

| 文件 | 用途 |
|---|---|
| `CLAUDE.md` | Claude Code 執行開發時的根指令。請先讀這份。 |
| `docs/AetherCare_MVP_Master_Spec.md` | AetherCare MVP 主規格。後續產品、前端、後端、AI、schema 實作以這份為準。 |
| `docs/AetherCare_AI_Care_Chat_Spec.md` | 事件綁定 AI 處置助手規格。用來實作照顧者端基礎 AI 對話。 |
| `docs/AetherCare_MVP_Gap_Review.md` | 重新檢視後的缺口、補足項目與已採用決策。 |
| `docs/AetherCare_MVP_Implementation_Roadmap.md` | 可交給工程實作的分階段 roadmap、測試情境與完成標準。 |
| `aethercare_codex_system_design.md` | 原始系統設計與 workflow engine 詳細草案。 |
| `aethercare_frontend_backend_api_integration.md` | 原始前後端 API、AI guidance、UX 規格草案。 |
| `AetherCare_PostgreSQL_Schema.sql` | 目前 PostgreSQL schema 草案。實作前請依 master spec 再做 migration 整理。 |
| `LZA.md` | LZA 品牌架構。 |
| `起草書.txt` | 柳智品牌敘事與創業原始宣言。 |

## Canonical Product Statement

> AetherCare 是一套讓被照顧者能被聽見、讓照顧者不再孤軍奮戰、讓每一次照護事件都被接住的 AI 居家照護系統。

## Canonical Terms

| 中文 | 英文 | 說明 |
|---|---|---|
| 被照顧者 | care recipient | 系統照護對象。避免在新文件中使用 elder 作為通用名稱。 |
| 照顧者 | caregiver | 家屬、看護、鄰居、機構人員等可接任務者。 |
| 原始訊號 | activity log / signal | check-in、SOS、手機最後活動、手動回報、無回應等原始資料。 |
| 照護事件 | care event | 需要追蹤或處理的事件，例如 SOS、未回報、無活動、疑似跌倒。 |
| 照護任務 | care task | 指派給某位照顧者、帶有 SLA 的任務。 |
| 責任鏈 | audit timeline | 事件、通知、確認、升級、處置的完整紀錄。 |

## SLA 與升級術語

AetherCare 的 SLA（Service Level Agreement）是「事件被正確的人接住」的時限承諾，不是法律意義上的服務水準合約。這套術語對應 `docs/AetherCare_MVP_Master_Spec.md §9 SLA Defaults` 與 backend 的 `CareTaskTimeoutScanner` / `MissedCheckInScheduler` / `NoActivityScheduler` 實作。

### 核心欄位

| 中文 | 英文 | 對應欄位 / 程式名稱 | 說明 |
|---|---|---|---|
| 處理時限 | SLA seconds | `care_contact_escalation.sla_seconds` | 一筆 task 從建立到 deadline 的秒數，依 (event type × risk × level) 設定。 |
| 截止時間 | deadline | `care_task.deadline_at` | task 建立時 = `now + sla_seconds`，過了仍 PENDING 即視為逾時。 |
| 寬限期 | grace period | `recipient_observation_settings.checkin_grace_minutes` | 預期 check-in 之後再容忍多少分鐘才產生 `MISSED_CHECK_IN` 事件。 |
| 倒數 | countdown / time-to-deadline | `SlaInfo.remainingSeconds` | dashboard 與 event detail 的 SLA 倒數，前端每 5 秒輪詢更新。 |
| 即將到期 | near expiry | `AiCareChatRulesEngine` 的 2 分鐘規則 | 剩餘時間 ≤ 120 秒時 AI Chat 觸發 ESCALATE 提醒，避免靜默逾時。 |

### 狀態 / 流程

| 中文 | 英文 | 對應欄位 / 程式名稱 | 說明 |
|---|---|---|---|
| 待處理 | pending | `care_task.status = 'PENDING'` | 任務剛指派、尚未被照顧者觸碰；timeout scanner 只會掃這個狀態。 |
| 已確認收到 | acknowledged | `care_task.status = 'ACKNOWLEDGED'` | 照顧者按「我收到了」，**workflow 不轉終態**也不抑制升級（除非另行延長 SLA）。 |
| 逾時 | timeout | `care_task.status = 'TIMEOUT'` + audit `TASK_TIMEOUT` | scanner 用 conditional update 標記，保證**只有一個 worker** 推動升級。 |
| 升級 | escalation / escalate | audit `TASK_ESCALATED`、`ESCALATION_TRIGGERED` | 把責任轉給下一順位聯絡人；可由 timeout 或 caregiver 主動 `NEED_HELP` 觸發。 |
| 結案 | resolution / resolved | `workflow.status = 'RESOLVED'` + audit `WORKFLOW_RESOLVED` | 必須由人按 `CONFIRM_SAFE`（spec §3「確認安全 → 必須由人明確操作」）。 |
| 無人接住 | unresolved | `workflow.status = 'UNRESOLVED'` + audit `WORKFLOW_UNRESOLVED` | 無下一順位可升級時的安全收尾，事件**留 audit、不假裝結案**。 |

### 順位 / 接力

| 中文 | 英文 | 對應欄位 / 程式名稱 | 說明 |
|---|---|---|---|
| 升級層級 | escalation level | `care_task.level`、`workflow.current_level` | 1 = 主要照顧者、2 = 第二聯絡人、3 = 第三順位（部分 event type 沒 level 3）。 |
| 第 N 順位聯絡人 | level-N contact | `care_contact_escalation.contact_user_id` (level=N) | 對應 spec §9 SLA Defaults 表中每個 risk × level 的對象。 |
| 通知失敗 | notification failure | `notification_record.delivery_status` + audit `NOTIFICATION_SENT` 訊息 | mock notification 失敗不阻塞 workflow，會記錄並讓下一輪 SLA 接續處理。 |
| 條件式升級 | conditional update | `CareTaskRepository.markTimeoutIfPending` | 用 SQL `WHERE status = 'PENDING'` 防雙 worker race，確保「**只升一次**」。 |

### 規則對照表（spec § Master §9 SLA Defaults）

| Event Type | Risk | L1 | L2 | L3 |
|---|---:|---:|---:|---:|
| `SOS` / `FALL_DETECTED` | HIGH | 30s | 60s | 120s |
| `POSSIBLE_FALL` | HIGH | 60s | 120s | 180s |
| `NO_ACTIVITY` | MEDIUM | 5m | 10m | 30m |
| `MISSED_CHECK_IN` / `NO_RESPONSE` | MEDIUM | 10m | 20m | 60m |
| `DAILY_REMINDER` | LOW | 1h | 2h | — |

> 設計原則：HIGH risk 的 L1 SLA 故意設得短（30 秒），用「**逾時即升級**」當保險，避免單一照顧者沉默就失控。MEDIUM 給人合理回電時間，LOW 不要打擾。

### 關鍵實作不變式

1. **不可靜默結案**：scanner 永遠不會把 workflow 直接改成 RESOLVED。終態必須由 `CONFIRM_SAFE` 或外部觸發。
2. **不可重複升級**：每個 PENDING task 在 race 條件下也只會被升級一次（conditional `UPDATE`）。
3. **無回應 ≠ 安全**（spec §3）：SLA 過期不代表沒事，是換人接力。
4. **AI 不能改 SLA / workflow state**（spec § AI_Care_Chat §9）：AI Chat 只能建議，按鈕仍要走 confirmation flow。

## Next Step

依序執行：

1. 讓 Claude Code 先讀 `CLAUDE.md`。
2. 用 `docs/AetherCare_MVP_Master_Spec.md` 鎖定 MVP 產品與系統範圍。
3. 用 `docs/AetherCare_MVP_Implementation_Roadmap.md` 建立實際前後端專案。
4. 用 `docs/AetherCare_MVP_Gap_Review.md` 檢查不要回到舊命名或舊範圍。
