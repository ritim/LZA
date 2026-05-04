# AetherCare MVP Master Spec

Version: 0.3  
Owner: LZA / LiuZhi AetherCare Systems  
Status: Canonical MVP specification for Claude Code implementation

## 0. Implementation Defaults for Claude Code

Claude Code should treat this file as the source of truth.

Fixed implementation decisions:

- Build a modular monolith, not microservices.
- Backend: Java 21, Spring Boot 3.x, Maven, Spring Web, Spring Data JPA, Spring Validation.
- Database: PostgreSQL. Use the table names in `AetherCare_PostgreSQL_Schema.sql`.
- Frontend: Next.js, TypeScript, Tailwind CSS, TanStack Query.
- Authentication for MVP: mock identity headers.
  - Caregiver APIs use `X-Caregiver-Id`.
  - Recipient APIs use `X-Care-Recipient-Id` or request body `careRecipientId` during demo.
- Notification for MVP: mock notification service that writes `notification_records`.
- AI for MVP: static JSON guidance files first. Do not block workflow on LLM availability.
- Event bus for MVP: no Kafka. Use synchronous service calls and a scheduled timeout scanner.
- Timezone: store timestamps as `timestamptz`; display demo time in `Asia/Taipei`.
- Canonical naming:
  - Tables use plural snake_case: `care_events`, `care_tasks`.
  - APIs use plural resources: `/api/v1/care-events`, `/api/v1/care-recipients`.
  - Code types use singular PascalCase: `CareEvent`, `CareTask`.

## 1. Product Core

AetherCare 是一套讓被照顧者能被聽見、讓照顧者不再孤軍奮戰、讓每一次照護事件都被接住的 AI 居家照護系統。

MVP 第一個要證明的不是「AI 很強」，而是：

```text
事件發生
→ 系統知道
→ 正確的人收到
→ 有人在期限內確認
→ 沒有人處理就自動升級
→ 全流程留下責任鏈
```

### MVP 不做

- 不做醫療診斷。
- 不做機器人硬體。
- 不要求一開始接真實 IoT 裝置。
- 不做完整多租戶機構權限。
- 不用 AI 自動判斷安全或自動關閉事件。

## 2. Users and Roles

### Care Recipient 被照顧者

被照顧者可能有三種表達模式：

| Mode | 中文 | 系統策略 |
|---|---|---|
| `ACTIVE` | 能主動表達 | 提供大按鈕 check-in、SOS、身體狀態回報。 |
| `LIMITED_EXPRESSION` | 表達不完整 | 允許簡單回答，並要求照顧者二次確認。 |
| `PASSIVE` | 無法主動表達 | 用未回報、無活動、手機最後活動、照顧者回報等被動訊號啟動 workflow。 |

### Caregiver 照顧者

照顧者包含家屬、看護、鄰居、機構人員或 call center。每位照顧者可以有：

- 通知順位。
- 可接收事件類型。
- 可執行動作。
- 是否可關閉 workflow。

MVP uses mock caregiver identity via `X-Caregiver-Id`. Data models still keep caregiver membership and notification priority.

## 3. Core Principle: Silence Is a Signal

被照顧者沒有主動表達時，系統不可假設安全。

```text
不確定 → 要求確認
無法確認 → workflow 保持 open
逾時未確認 → 自動升級
確認安全 → 必須由人明確操作
```

這是 AetherCare 和一般提醒工具的差異：系統不只通知，而是持續追蹤到事件被接住。

## 4. MVP Experiences

### Recipient Web App

第一版做手機 Web App。畫面必須大字、高對比、少選項。

首頁只放核心操作：

```text
[我今天還好]
[我需要幫忙]
[身體不舒服]
[打給家人]
```

被照顧者端行為：

- 按 `我今天還好`：建立 `CHECK_IN` activity log。
- 按 `我需要幫忙`：建立 `SOS_PRESSED` signal，產生 HIGH care event，啟動 workflow。
- 按 `身體不舒服`：建立 `FEELING_UNWELL` signal，依回答與 profile 判斷風險。
- 沒有按預期 check-in：由 rule engine 建立 `MISSED_CHECK_IN` care event。

### Caregiver Web App

照顧者端不是監控 dashboard，而是事件處理台。它必須回答三件事：

```text
發生什麼事？
我現在該做什麼？
如果我不處理會怎樣？
```

必要畫面：

- Dashboard：今日狀態、active care events、SLA 倒數、最近 timeline。
- Event Detail：事件內容、AI guidance、評估問題、行動按鈕、audit timeline。
- Care Profile：被照顧者資料、baseline、照顧者順位、近期事件。

### Workflow / AI / Audit Core

後端核心負責：

- 接收 signals。
- rule-based event detection。
- 建立 workflow 與 care task。
- 發送通知或 mock notification。
- SLA timeout scanner。
- escalation。
- action validation。
- audit timeline。
- AI guidance 或 static fallback guidance。

## 5. Canonical Domain Model

### Raw Signal Layer

`activity_logs` 存所有原始訊號：

```text
CHECK_IN
SOS_PRESSED
FEELING_UNWELL
PHONE_LAST_SEEN
CAREGIVER_MANUAL_REPORT
NO_RESPONSE
MOTION_DETECTED
NO_ACTIVITY_WINDOW
```

### Event Layer

`care_events` is the canonical persistence table and API resource for照護事件. Do not create or use `anomaly_events` in new implementation.

MVP care event types：

```text
SOS
FEELING_UNWELL
MISSED_CHECK_IN
NO_ACTIVITY
NO_RESPONSE
POSSIBLE_FALL
FALL_DETECTED
```

Risk levels：

```text
LOW
MEDIUM
HIGH
CRITICAL
```

### Workflow Layer

每個需要處理的 care event 必須建立 workflow。

Workflow statuses：

```text
NEW
WAITING_RESPONSE
ACKNOWLEDGED
ESCALATED
RESOLVED
UNRESOLVED
CANCELLED
```

Task statuses：

```text
PENDING
ACKNOWLEDGED
COMPLETED
TIMEOUT
ESCALATED
CANCELLED
```

Caregiver action types：

```text
CONFIRM_SAFE
CALL_ELDER
CALL_NO_ANSWER
REQUEST_ON_SITE_CHECK
ESCALATE
CALL_SECOND_CONTACT
CALL_EMERGENCY
MARK_UNABLE_TO_CONFIRM
REQUEST_HELP
ADD_NOTE
```

`CALL_NO_ANSWER`、`MARK_UNABLE_TO_CONFIRM`、`REQUEST_ON_SITE_CHECK` 不可直接 resolve workflow。

### Audit Layer

每個重要行為都要寫 audit timeline：

```text
SIGNAL_RECEIVED
CARE_EVENT_CREATED
WORKFLOW_STARTED
TASK_CREATED
NOTIFICATION_SENT
AI_GUIDANCE_GENERATED
ASSESSMENT_ANSWERED
ACTION_RECEIVED
CALL_NO_ANSWER_RECORDED
ON_SITE_CHECK_REQUESTED
TASK_TIMEOUT
TASK_ESCALATED
WORKFLOW_RESOLVED
WORKFLOW_UNRESOLVED
```

## 6. Core Flows

### Flow A: Daily Check-in Success

```text
被照顧者按「我今天還好」
→ create activity_log CHECK_IN
→ update latest status
→ caregiver dashboard 顯示今日已確認
→ no workflow unless there is an open event requiring human confirmation
```

### Flow B: SOS

```text
被照顧者按「我需要幫忙」
→ create activity_log SOS_PRESSED
→ create HIGH care event
→ start workflow
→ create level 1 task
→ notify primary caregiver
→ caregiver opens event detail
→ AI guidance appears
→ caregiver selects one fixed action from the allowed action set
→ resolve or escalate
```

### Flow C: Missed Check-in / Passive Recipient

```text
expected_checkin_time + grace passed
→ no CHECK_IN found
→ create MISSED_CHECK_IN care event
→ start workflow
→ notify level 1 caregiver
→ caregiver calls but no answer
→ action CALL_NO_ANSWER recorded
→ workflow remains WAITING_RESPONSE
→ AI recommends escalation or on-site check
→ SLA timeout escalates to level 2
→ second caregiver confirms safe
→ workflow RESOLVED
```

### Flow D: No Activity / Possible Fall

```text
last activity exceeds threshold
→ rule engine evaluates mobility, fall history, daytime/nighttime baseline
→ create NO_ACTIVITY or POSSIBLE_FALL event
→ HIGH risk when low mobility + long inactivity or fall history
→ workflow starts
→ caregiver must confirm safety, request on-site check, escalate, or call emergency
```

## 7. API Surface

### Recipient APIs

```http
POST /api/v1/recipient/check-ins
POST /api/v1/recipient/sos
POST /api/v1/recipient/status-reports
GET  /api/v1/recipient/today
```

### Caregiver APIs

```http
GET  /api/v1/caregiver/dashboard
GET  /api/v1/care-events/{eventId}
GET  /api/v1/workflows/{workflowId}
GET  /api/v1/workflows/{workflowId}/timeline
GET  /api/v1/workflows/{workflowId}/ai-messages
POST /api/v1/workflows/{workflowId}/assessment-answers
POST /api/v1/workflows/{workflowId}/actions
GET  /api/v1/ai/care-guidance?eventId={eventId}&workflowId={workflowId}
POST /api/v1/ai/care-chat
GET  /api/v1/care-recipients/{careRecipientId}
GET  /api/v1/care-recipients/{careRecipientId}/contacts
GET  /api/v1/care-recipients/{careRecipientId}/observation-settings
PUT  /api/v1/care-recipients/{careRecipientId}/observation-settings
```

## 8. AI Guidance Rules

AI is a care support assistant, not a medical authority.

AI must:

- Provide short situation summary.
- Provide clear next steps.
- Ask assessment questions.
- Highlight danger signs.
- Recommend escalation when uncertain.
- Return structured JSON.
- Include disclaimer.

AI must not:

- Diagnose disease.
- Say the person is definitely safe.
- Treat no response as safe.
- Tell caregiver to delay emergency care.
- Close workflow automatically.

MVP must return static knowledge JSON. LLM integration is post-MVP unless explicitly requested later. Required knowledge files:

```text
fall.json
no_activity.json
missed_check_in.json
no_response.json
feeling_unwell.json
breathing_issue.json
stroke.json
wandering.json
```

### 8.1 AI Care Chat

AI Care Chat is the basic caregiver conversation layer for sudden events. It must follow `docs/AetherCare_AI_Care_Chat_Spec.md`.

Rules:

- It lives inside the event detail page only.
- It is bound to a `careEventId`, `workflowId`, and current task.
- It uses static guidance and deterministic rules for MVP.
- It saves user and assistant messages in `ai_chat_messages`.
- It may suggest workflow actions, but it must not execute them.
- Suggested actions become buttons that call the workflow action API after caregiver confirmation.
- It must not be implemented as a standalone general chatbot.

## 9. SLA Defaults

| Event Type | Risk | Level 1 | Level 2 | Level 3 |
|---|---:|---:|---:|---:|
| `SOS` | HIGH | 30 sec | 60 sec | 120 sec |
| `FALL_DETECTED` | HIGH | 30 sec | 60 sec | 120 sec |
| `POSSIBLE_FALL` | HIGH | 60 sec | 120 sec | 180 sec |
| `NO_ACTIVITY` | MEDIUM | 5 min | 10 min | 30 min |
| `MISSED_CHECK_IN` | MEDIUM | 10 min | 20 min | 60 min |
| `NO_RESPONSE` | MEDIUM | 10 min | 20 min | 60 min |
| `DAILY_REMINDER` | LOW | 1 hour | 2 hours | no level 3 |

SLA expiration must create audit logs and only one worker may perform escalation.

## 10. Acceptance Criteria

The MVP is successful when the demo can show:

- A recipient check-in updates caregiver dashboard.
- A missed check-in creates a workflow for a passive recipient.
- A no-activity event creates AI guidance with uncertainty-aware questions.
- AI Care Chat helps with a sudden event while keeping workflow state unchanged until caregiver action confirmation.
- `CALL_NO_ANSWER` records action but does not close workflow.
- SLA timeout escalates to the next caregiver.
- `CONFIRM_SAFE` by a caregiver resolves workflow.
- Timeline shows signal, event, notification, AI guidance, action, timeout/escalation, and resolution.
- AI failure falls back to static guidance without blocking workflow.
