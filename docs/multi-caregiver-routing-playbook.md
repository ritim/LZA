# Multi-Caregiver LINE 路由手動驗收 Playbook

> 目的：驗證 escalation 鏈在多家屬下會正確把通報路由到「對的人」的 LINE。
> 適用日期：2026-05-24 起（含 0025 escalation seed、0024 elder phone、0023 line binding）。

---

## 角色 / 帳號對照

| 帳號 | app_user.id | 角色 | 對應綁定 LINE userId |
|---|---|---|---|
| `family01` / `family123` | 2 | level 1 主要照顧者 | 由你綁定（個人手機） |
| `family02` / `family123` | 3 | level 2 次要照顧者 | 找另一支手機 / 同事測試 |
| `admin` / `admin123` | 1 | 管理員 | 不綁，用來看 dashboard |

對應 elder：

| elder_id | 名稱 | 用途 | escalation 鏈 |
|---|---|---|---|
| 301 | 王美玉 | 真實 demo | level 1 → family01 (LINE 30s) / level 2 → family02 (LINE 60s) |
| 1001 | 王美玉（Demo） | dashboard demo button | level 1 → family01 (LINE 30s) / level 2 → family02 (SMS 60s) |

> 建議用 elder=301，兩層都 LINE，能完整驗 LINE 推播鏈。

---

## 前置作業（一次性）

1. **服務齊備**：依 [`docs/sessions/2026-05-24-line-push-pipeline.md`](sessions/2026-05-24-line-push-pipeline.md#服務啟動順序dev) 起 docker / backend / frontend / cloudflared。
2. **兩支手機 / 兩個 LINE 帳號**都加 LINE OA 為好友。
3. **family01 綁定** LINE：
   - 用 family01 登入 → Dashboard → LineBindingCard → 點「產生綁定碼」
   - 用「家屬 A 的 LINE」傳 8 字元碼給 OA
   - OA 回「✅ 綁定成功」即成功，DB 可驗：
     ```sql
     SELECT caregiver_id, line_user_id, line_display_name
       FROM caregiver_line_binding WHERE caregiver_id = 2;
     ```
4. **family02 綁定**：登出，改登入 family02，重複步驟 3，用「家屬 B 的 LINE」傳碼。
5. **DB 驗 escalation 鏈**就緒：
   ```sql
   SELECT level, contact_user_id, channel, sla_seconds, enabled
     FROM care_contact_escalation
    WHERE elder_id = 301
    ORDER BY level;
   -- 期望：1, 2, LINE, 30, t / 2, 3, LINE, 60, t
   ```

---

## 場景 1：SOS（level 1 立刻通報 family01）

**目標**：證明 SOS event 一建立就把 LINE 訊息送到 family01 那支手機。

```bash
TOKEN=$(curl -s http://localhost:9001/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"family01","password":"family123"}' | jq -r .accessToken)

curl -s -X POST http://localhost:9001/api/v1/recipient/sos \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Care-Recipient-Id: 301" \
  -H 'Content-Type: application/json' \
  -d '{"note":"playbook 場景 1"}'
```

**期望**：
- family01 LINE 收到 Flex 卡片（🚨 緊急求助 SOS / 任務 #X / level 1）含「📞 打給王美玉」+「我收到了」兩顆按鈕
- family02 **不會**收到（還沒升級）
- DB 驗：
  ```sql
  SELECT id, assignee_id, level, status FROM care_task
   WHERE event_id = (SELECT MAX(id) FROM care_event WHERE event_type='SOS' AND elder_id=301);
  -- 期望：assignee_id = 2, level = 1, status = PENDING
  ```

---

## 場景 2：SLA timeout → 升級到 family02

**目標**：驗證 family01 沒回應、SLA 過期後，task 自動升級、family02 LINE 收到通報。

> SOS level 1 SLA 預設 30 秒（見 escalation 表）。延續場景 1，不要按 family01 LINE 的「我收到了」，等 ≥30s。

**等待約 35 秒後**：
```bash
# 看 timeout scanner 是否生成 level 2 task
psql -h localhost -U aethercare -d aethercare -c "
  SELECT id, assignee_id, level, status, created_at, deadline_at
    FROM care_task
   WHERE workflow_id = (SELECT MAX(id) FROM care_workflow_instance)
   ORDER BY level;
"
# 期望：
#   - level=1 row：status=TIMEOUT
#   - level=2 row：status=PENDING, assignee_id=3
```

**LINE 端期望**：
- family01 不再收到新訊息（已 TIMEOUT，不會再 push）
- family02 LINE 收到 Flex 卡片（緊急求助 SOS / 任務 #X+1 / level 2）

**Audit 驗證**：
```bash
curl -s http://localhost:9001/api/v1/workflows/<workflowId>/audit-logs \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[] | .action'
```
應包含順序：`EVENT_CREATED`, `WORKFLOW_STARTED`, `TASK_CREATED (level 1)`,
`NOTIFICATION_SENT`, `TASK_TIMEOUT`, `TASK_ESCALATED`, `TASK_CREATED (level 2)`,
`NOTIFICATION_SENT`. （scanner timeout 走 `TASK_ESCALATED`；caregiver 點 NEED_HELP 升級才用 `ESCALATION_TRIGGERED`）

---

## 場景 3：family02 在 LINE 上按「我收到了」→ task ACKNOWLEDGED

**目標**：驗 postback 真接 `CareActionService.acknowledge` 而非僅 reply。

延續場景 2 — 在 family02 那支手機按 LINE Flex 上的「我收到了」按鈕。

**期望**：
- LINE 回：「✅ 已收到任務 #X+1。請至 AetherCare Dashboard 完成後續處理。」
- DB 驗：
  ```sql
  SELECT status, acknowledged_at FROM care_task WHERE id = <task id>;
  -- 期望 status = ACKNOWLEDGED, acknowledged_at IS NOT NULL
  ```
- `care_action` 應多一筆 actor_id=3 / action_type=ACKNOWLEDGE
- `care_audit_log` 應有 `TASK_ACKNOWLEDGED` action

---

## 場景 4：Dashboard 顯示綁定狀態（兩個帳號交叉看）

**目標**：驗 ActiveEventCard 正確顯示「派發給：XXX」+ LINE 綁定 chip。

1. 登入 family01 → Dashboard 上應**看不到** SOS 卡片（因 task 已升級到 family02）
2. 登入 family02 → Dashboard 上看到 SOS 卡片，內容：
   ```
   派發給：家屬二號（level 2）
   [📱 LINE：陳家的暱稱]   ← 綠色 chip
   ```
3. 若 family02 還沒綁定 LINE，chip 應顯示 `⚠️ 未綁 LINE`（灰色）

---

## 場景 5：未綁 LINE 的 caregiver 按按鈕（負面）

**目標**：未綁 LINE 但有人偽造 postback 應被 webhook 拒絕。

很難真實重現（要從別人的 LINE 送），可用 SQL 驗證行為：

```sql
DELETE FROM caregiver_line_binding WHERE caregiver_id = 3;
-- 然後讓 LINE OA 用 family02 那支手機按按鈕（已解綁）
-- 期望 LINE 回：「⚠️ 您的 LINE 尚未綁定任何照護者帳號…」
-- 期望 care_task 狀態不變（沒被 ACKNOWLEDGE）
```

---

## 清場（測試之間）

```sql
-- 刪 SOS 事件鏈，方便重跑
DELETE FROM care_action WHERE task_id IN (
  SELECT id FROM care_task WHERE event_id IN (
    SELECT id FROM care_event WHERE event_type='SOS' AND elder_id=301
  )
);
DELETE FROM care_audit_log WHERE event_id IN (
  SELECT id FROM care_event WHERE event_type='SOS' AND elder_id=301
);
DELETE FROM care_task WHERE event_id IN (
  SELECT id FROM care_event WHERE event_type='SOS' AND elder_id=301
);
DELETE FROM care_workflow_instance WHERE event_id IN (
  SELECT id FROM care_event WHERE event_type='SOS' AND elder_id=301
);
DELETE FROM care_event WHERE event_type='SOS' AND elder_id=301;
```

---

## 已知坑

- **escalation 升級不等於通知會到**：要 family02 真的綁過 LINE 才會 push；沒綁也會建 task，dashboard 卡片會顯示 `⚠️ 未綁 LINE`。
- **30 秒 SLA**：dev 看著等可以，production 不要照搬。
- **同 LINE userId 不能綁兩個 caregiver**：表上有 unique constraint。要測 family02，請用另一支手機 / 另一個 LINE 帳號。
- **family01 同時在線**：兩個 family 都登 dashboard 時，cookie / token 是 per-browser 的，建議用「兩個瀏覽器 profile」或一個 Chrome + 一個無痕。
