# Manual Acceptance Playbook

對應 spec **§ Master §10 Acceptance Criteria** + **CLAUDE.md Frontend tests or manual verification** 5 項。
後端 IT（Testcontainers）已覆蓋資料面，本 playbook 是 demo / 上線前**人眼驗收 UI 行為**的步驟書。

## 0. Prerequisites

```bash
# Terminal 1：後端
cd aethercare-api && ./gradlew bootRun

# Terminal 2：前端
cd aethercare-web && npm run dev
```

兩個 service 都跑起來（後端 :8080、前端 :5173，vite proxy 已設）。

Demo seed：
- Care recipient `id=1001`（demo 王美玉）
- Caregiver `family01 / family123`
- Caregiver `family02 / family123`

## 1. Recipient check-in updates caregiver dashboard

**Steps**
1. 開分頁 A：`http://localhost:5173/recipient?id=1001`，按 **「我今天還好」**，看到 toast「已通知家人您今天平安」。
2. 開分頁 B：`http://localhost:5173/login`，以 `family01` 登入。
3. Dashboard 自動跳轉，等最多 5 秒（poll interval）。

**Expected**
- 「今日狀態」卡片的 **最近 check-in** tile 顯示剛剛的時間（例如 `04/27 16:30`），相對時間「剛剛」或「X 分前」。
- 「進行中事件」區仍為空（CHECK_IN 不啟動 workflow，spec §0）。

## 2. SOS appears as active event

**Steps**
1. 分頁 A `/recipient?id=1001` 按 **「我需要幫忙」** → 確認 dialog → toast 顯示事件編號。
2. 切到分頁 B dashboard。

**Expected**
- 「進行中事件」出現一筆 SOS 事件，被照顧者「王美玉（Demo）（82歲）」、風險 HIGH、SLA 倒數中。
- 「今日狀態」卡片：**進行中事件 = 1**、**等待回應 = 1**、**警報數 = 1**。
- Click 該事件 → 跳到 `/caregiver/events/{eventId}`。

## 3. Event detail shows guidance and timeline

**Steps（接續 #2 的事件）**
1. 在 event detail page 等待 AI guidance 載入（static 知識庫，幾乎瞬間）。

**Expected**
- 上方顯示風險 / 被照顧者 ID / 地點 / 發生時間 / 目前狀態。
- AI Guidance 區顯示：
  - summary（事件摘要）
  - guidance[]（建議步驟）
  - questions[]（評估問題，可勾選）
  - suggestedActions[]（按鈕清單）
  - dangerSigns[]（危險徵兆）
  - disclaimer（免責）
- 下方 Audit Timeline 顯示至少 4 筆：`EVENT_CREATED`、`WORKFLOW_STARTED`、`TASK_CREATED`、`NOTIFICATION_SENT`。

## 4. 電話未接 keeps workflow open（spec § Gap D 核心）

**Steps（接續 #2 的事件）**
1. 在 event detail action bar 找「**電話未接**」按鈕（CareActionType `CALL_NO_ANSWER`）。
2. 按下 → 確認 dialog → 提交。

**Expected**
- Workflow status badge **仍為 `WAITING_RESPONSE`**（不轉 RESOLVED 也不 ESCALATED）。
- Audit Timeline 多一筆 `ACTION_RECEIVED` 或同等紀錄，內容含「電話未接」。
- 「進行中事件」卡片仍存在，**不會消失**。
- AI guidance 仍可看，提示繼續嘗試聯絡 / 通知第二聯絡人。

## 5. SLA expiration displays escalation state

**Steps**
1. 在後端壓 SLA 為 5 秒（IT setUp 慣用做法）：
   ```bash
   psql -h localhost -U aethercare -d aethercare \
     -c "UPDATE care_contact_escalation SET sla_seconds = 5 WHERE level = 1;"
   ```
2. 分頁 A 重新按 SOS → 切到分頁 B dashboard，**不要按任何 action**。
3. 等 ~10 秒（timeout scanner fixed-delay 預設 5s）。

**Expected**
- 「過期任務」tile 變紅且顯示 1。
- 進行中事件卡片內 SLA 倒數變紅 / 顯示「已逾期」。
- 自動建立 level=2 task；若該事件詳情頁 refresh，看到 task 列表多一筆 level=2、assignee=family02。
- Audit Timeline 多兩筆：`TASK_TIMEOUT` + `TASK_ESCALATED`。

完成後記得把 SLA 還原：
```bash
psql -h localhost -U aethercare -d aethercare \
  -c "UPDATE care_contact_escalation SET sla_seconds = 30 WHERE level = 1;"
```

## Pass criteria

5 項全部觀察到預期行為即視為 acceptance 通過；任一項不符請對照 backend IT
（`FallDetectedEndToEndIT` / `CallNoAnswerKeepsWorkflowOpenIT` / `RecipientSelfEndToEndIT`）
的 assertion 找回歸點。
