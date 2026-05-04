# AetherCare AI Care Chat Implementation Spec

Status: Claude Code implementation spec  
Scope: Caregiver event detail AI conversation for MVP  
Canonical principle: AI guides care actions; workflow controls state.

## 1. Product Intent

AI Care Chat is not a general chatbot. It is an event-bound care handling assistant inside the caregiver event detail page.

It helps caregivers during sudden or uncertain events by:

- Summarizing the current event context.
- Asking the next useful assessment question.
- Explaining danger signs.
- Suggesting workflow-safe actions.
- Keeping uncertainty visible.
- Recording AI guidance in the timeline.

AI Care Chat must not:

- Diagnose disease.
- Say the recipient is definitely safe.
- Treat no response as safe.
- Close workflow automatically.
- Create hidden workflow actions without caregiver confirmation.
- Replace emergency services.

## 2. UX Placement

Place AI Care Chat inside:

```text
/caregiver/events/[eventId]
```

The event detail page order:

```text
Event header
SLA countdown
AI guidance card
AI Care Chat panel
Fixed action buttons
Audit timeline
```

The chat panel must always be tied to a `careEventId`, `workflowId`, and current `careTaskId`.

Do not build a standalone `/chat` page for MVP.

## 3. First Message Behavior

When caregiver opens the event detail page, the system should show an initial AI message generated from static guidance and workflow context.

Example for `MISSED_CHECK_IN`:

```text
王美玉尚未完成 09:00 平安回報，目前已超過 grace period。
這不代表危險已發生，但也不能視為安全。
請先嘗試電話聯絡，若電話未接，建議通知下一順位或請人到場確認。
```

Initial AI message must be saved as an assistant message with source `STATIC_GUIDANCE`.

## 4. API

### POST /api/v1/ai/care-chat

Purpose:

Send a caregiver message and return a structured AI care response.

Request:

```json
{
  "careEventId": 501,
  "workflowId": 9001,
  "taskId": 7001,
  "message": "我打電話沒有人接，現在怎麼辦？"
}
```

Headers:

```http
X-Caregiver-Id: 1001
```

Response:

```json
{
  "messageId": 3002,
  "workflowId": 9001,
  "careEventId": 501,
  "reply": "電話未接時，請不要視為安全。建議通知第二順位照顧者，或請附近的人到場確認。",
  "questions": [
    {
      "id": "q_last_activity",
      "question": "最後一次活動或 check-in 是什麼時候？",
      "type": "TEXT"
    },
    {
      "id": "q_on_site",
      "question": "是否有人可以到場確認？",
      "type": "YES_NO_UNKNOWN",
      "options": ["是", "否", "不確定"],
      "dangerAnswer": ["否", "不確定"]
    }
  ],
  "suggestedActions": [
    {
      "type": "ESCALATE",
      "label": "通知下一位照顧者",
      "priority": "HIGH",
      "confirmationRequired": true
    },
    {
      "type": "REQUEST_ON_SITE_CHECK",
      "label": "請人到場確認",
      "priority": "HIGH",
      "confirmationRequired": true
    },
    {
      "type": "CALL_NO_ANSWER",
      "label": "記錄電話未接",
      "priority": "MEDIUM",
      "confirmationRequired": true
    }
  ],
  "disclaimer": "此建議不能取代醫療診斷。若有危急狀況，請立即聯絡緊急服務。",
  "generatedAt": "2026-05-02T10:03:00+08:00"
}
```

Backend behavior:

- Validate caregiver can access the workflow.
- Save caregiver message in `ai_chat_messages`.
- Build context from event, recipient profile, workflow, current task, timeline, prior actions, and static guidance.
- Generate deterministic MVP response from rules and static guidance.
- Save assistant reply in `ai_chat_messages`.
- Write audit log action `AI_CHAT_MESSAGE_CREATED`.
- Return structured response.

### GET /api/v1/workflows/{workflowId}/ai-messages

Purpose:

Load AI Care Chat history for the workflow.

Response:

```json
{
  "workflowId": 9001,
  "items": [
    {
      "id": 3001,
      "role": "ASSISTANT",
      "source": "STATIC_GUIDANCE",
      "message": "王美玉尚未完成 09:00 平安回報...",
      "structuredJson": {
        "suggestedActions": ["CALL_ELDER", "ESCALATE"]
      },
      "createdAt": "2026-05-02T10:00:10+08:00"
    }
  ]
}
```

## 5. Context Builder

AI Care Chat must use event, recipient, workflow, task, timeline, prior actions, and care knowledge. Do not answer from the caregiver message alone.

Minimum context fields:

```text
careEvent.id
careEvent.eventType
careEvent.riskLevel
careRecipient.id
careRecipient.expressionMode
careRecipient.mobility
workflow.id
workflow.status
workflow.deadlineAt
currentTask.id
currentTask.status
recentTimeline[]
priorActions[]
knowledge.eventType
```

## 6. Deterministic MVP Response Rules

Use rules before LLM.

### Rule: No response or call no answer

If message or prior action indicates `CALL_NO_ANSWER`:

- Reply must say no response is not proof of safety.
- Suggested actions must include `ESCALATE` and `REQUEST_ON_SITE_CHECK`.
- Suggested actions may include `CALL_EMERGENCY` only when risk is `HIGH` or `CRITICAL`.
- Workflow must remain open.

### Rule: Confirmed safe

If caregiver says the recipient is safe:

- AI may suggest `CONFIRM_SAFE`.
- AI must remind caregiver that confirmation is recorded in the responsibility timeline.
- AI must not mark safe automatically.

### Rule: Danger signs

If caregiver mentions unconsciousness, breathing issue, chest pain, severe pain, head impact, bleeding, or unable to move:

- Suggested first action must be `CALL_EMERGENCY`.
- Suggested secondary action may be `ESCALATE`.
- Reply must be concise and urgent.

### Rule: SLA near expiry

If current task deadline is within 2 minutes:

- Reply must mention the remaining urgency.
- Suggested actions must include `ESCALATE`.

## 7. Frontend Requirements

AI Care Chat panel:

- Shows initial assistant message.
- Shows short chat history.
- Provides a text input with placeholder: `描述目前狀況或詢問下一步`.
- Provides quick chips: `電話未接`, `已聯絡到`, `有人可到場`, `狀況不明`, `有危險徵兆`.
- Renders suggested actions as buttons, but action buttons must call workflow action APIs, not the chat API.
- Shows disclaimer below assistant responses.

The chat panel must not hide the fixed workflow action bar.

## 8. Data Model

Add table `ai_chat_messages`:

```sql
create table ai_chat_messages (
    id bigserial primary key,
    workflow_id bigint not null references care_workflow_instances(id),
    care_event_id bigint not null references care_events(id),
    task_id bigint references care_tasks(id),
    actor_user_id bigint references users(id),
    role varchar(20) not null,
    source varchar(30) not null,
    message text not null,
    structured_json jsonb,
    created_at timestamptz not null default now()
);
```

Roles: `USER`, `ASSISTANT`, `SYSTEM`.

Sources: `CAREGIVER_INPUT`, `STATIC_GUIDANCE`, `RULE_ENGINE`, `SYSTEM`.

## 9. Audit Requirements

Write `care_audit_logs` for:

```text
AI_CHAT_STARTED
AI_CHAT_MESSAGE_CREATED
AI_CHAT_SUGGESTED_ACTIONS
```

Do not write workflow action audit entries from chat alone. Workflow actions require explicit caregiver button confirmation.

## 10. Tests

Backend tests:

- `POST /api/v1/ai/care-chat` saves caregiver message and assistant reply.
- `CALL_NO_ANSWER` message returns `ESCALATE` and `REQUEST_ON_SITE_CHECK`.
- `CALL_NO_ANSWER` chat response does not change workflow status.
- Danger-sign message returns `CALL_EMERGENCY` as first suggested action.
- `GET /api/v1/workflows/{workflowId}/ai-messages` returns ordered history.

Frontend verification:

- Event detail displays AI Care Chat panel.
- Quick chip `電話未接` sends chat message and shows escalation/on-site suggestions.
- Suggested action buttons still require workflow action confirmation.
- Timeline shows AI chat audit entries separately from caregiver actions.
