# AetherCare AI — Frontend / Backend API Integration Spec
## For Claude Code Implementation

Version: v1.0  
Product: AetherCare AI — Home Care Copilot  
Scope: Caregiver UI + Workflow Engine + AI Assistant + Audit Trail

---

## 1. Goal

Build an MVP that allows a caregiver to receive a care event, view AI guidance, answer assessment questions, take actions, and update the workflow state with full audit logging.

Core flow:

```text
Care Event Created
  -> Workflow Started
  -> Task Created
  -> Notification Sent
  -> Caregiver Opens Event Detail
  -> AI Guidance Loaded
  -> Caregiver Answers Questions
  -> Caregiver Takes Action
  -> Workflow Updated
  -> Audit Timeline Updated
```

The frontend should guide the caregiver under pressure. The UI should not feel like a monitoring dashboard. It should answer:

```text
What happened?
What should I do now?
What happens if I do nothing?
```

---

## 2. Recommended Tech Stack

### Frontend

- Next.js or React
- TypeScript
- Tailwind CSS
- React Query / TanStack Query
- Zustand or simple React state for MVP
- Axios or fetch wrapper

### Backend

- Spring Boot 3.x
- Java 21
- REST API
- MySQL / PostgreSQL
- Redis for task locks and SLA cache
- Kafka optional for MVP; REST-first implementation is acceptable

### AI Assistant

- Separate backend module/service: `aethercare-ai-assistant`
- Calls LLM provider internally
- Uses care knowledge base and safety rules
- Returns structured JSON only

---

## 3. Frontend Pages

### 3.1 Dashboard Page

Route:

```text
/caregiver/dashboard
```

Purpose:

Show current care status and active events.

Main UI sections:

```text
1. Today Summary
2. Active Alerts
3. Elder Cards
4. Recent Timeline
```

Example card:

```text
🔴 王美玉（82歲）
疑似跌倒
地點：客廳
2 分鐘未回應
SLA 剩餘：00:18

[立即處理]
```

Primary API:

```http
GET /api/caregiver/dashboard
```

---

### 3.2 Event Detail Page

Route:

```text
/caregiver/events/{eventId}
```

Purpose:

This is the main emergency handling page.

Main UI sections:

```text
1. Event Header
2. SLA Countdown
3. AI Guidance Card
4. Assessment Questions
5. Action Buttons
6. Timeline
```

Header example:

```text
🚨 高風險事件
疑似跌倒

長者：王美玉
地點：客廳
發生時間：14:32
目前狀態：等待主要照顧者確認
SLA：剩餘 00:26
```

Primary APIs:

```http
GET /api/care-events/{eventId}
GET /api/workflows/{workflowId}
GET /api/ai/care-guidance?eventId={eventId}&workflowId={workflowId}
GET /api/workflows/{workflowId}/timeline
POST /api/workflows/{workflowId}/assessment-answers
POST /api/workflows/{workflowId}/actions
```

---

### 3.3 Event Timeline Page / Component

Route:

```text
/caregiver/events/{eventId}/timeline
```

Can also be embedded in Event Detail.

Timeline example:

```text
14:32 系統偵測疑似跌倒
14:32 已通知王先生
14:33 王先生查看事件
14:33 回答：長者清醒
14:34 已確認安全
14:34 Workflow 關閉
```

Primary API:

```http
GET /api/workflows/{workflowId}/timeline
```

---

### 3.4 Care Profile Page

Route:

```text
/caregiver/elders/{elderId}
```

Purpose:

Show elder profile, emergency contacts, recent events, and risk notes.

Primary APIs:

```http
GET /api/elders/{elderId}
GET /api/elders/{elderId}/contacts
GET /api/elders/{elderId}/events
```

---

## 4. API Design Overview

Base URL:

```text
/api
```

Authentication:

For MVP, use mock caregiver identity.

Header:

```http
X-Caregiver-Id: 1001
```

Future authentication:

```http
Authorization: Bearer <JWT>
```

---

## 5. Core Domain Models

### 5.1 CareEvent

```ts
export type CareEvent = {
  id: number;
  elderId: number;
  workflowId: number;
  type: CareEventType;
  riskLevel: RiskLevel;
  status: CareEventStatus;
  location?: string;
  detectedAt: string;
  createdAt: string;
  sensorSummary?: SensorSummary;
};
```

Enums:

```ts
export type CareEventType =
  | 'FALL'
  | 'NO_ACTIVITY'
  | 'STROKE_SUSPECTED'
  | 'BREATHING_ISSUE'
  | 'WANDERING'
  | 'MEDICATION_MISSED';

export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type CareEventStatus =
  | 'NEW'
  | 'PROCESSING'
  | 'RESOLVED'
  | 'ESCALATED'
  | 'CANCELLED';
```

---

### 5.2 WorkflowInstance

```ts
export type WorkflowInstance = {
  id: number;
  eventId: number;
  elderId: number;
  workflowType: string;
  riskLevel: RiskLevel;
  status: WorkflowStatus;
  currentLevel: number;
  currentTask?: CareTask;
  startedAt: string;
  completedAt?: string;
  createdAt: string;
};
```

Enums:

```ts
export type WorkflowStatus =
  | 'NEW'
  | 'ACTIVE'
  | 'WAITING_RESPONSE'
  | 'ACKNOWLEDGED'
  | 'RESOLVED'
  | 'TIMEOUT'
  | 'ESCALATED'
  | 'FAILED';
```

---

### 5.3 CareTask

```ts
export type CareTask = {
  id: number;
  workflowId: number;
  eventId: number;
  assigneeId: number;
  assigneeName: string;
  assigneeType: 'FAMILY' | 'CAREGIVER' | 'CALL_CENTER' | 'EMERGENCY';
  level: number;
  status: TaskStatus;
  deadlineAt: string;
  acknowledgedAt?: string;
  completedAt?: string;
  createdAt: string;
};
```

Enums:

```ts
export type TaskStatus =
  | 'PENDING'
  | 'ACKNOWLEDGED'
  | 'COMPLETED'
  | 'TIMEOUT'
  | 'ESCALATED'
  | 'CANCELLED';
```

---

### 5.4 ElderProfile

```ts
export type ElderProfile = {
  id: number;
  name: string;
  age: number;
  gender?: string;
  mobility: 'HIGH' | 'MEDIUM' | 'LOW';
  chronicDiseases?: string[];
  allergies?: string[];
  address?: string;
  emergencyNotes?: string;
};
```

---

### 5.5 AI Guidance

```ts
export type AICareGuidance = {
  summary: string;
  guidance: string[];
  questions: AssessmentQuestion[];
  suggestedActions: SuggestedAction[];
  dangerSigns: string[];
  disclaimer: string;
  generatedAt: string;
};

export type AssessmentQuestion = {
  id: string;
  question: string;
  type: 'YES_NO_UNKNOWN' | 'SINGLE_CHOICE' | 'TEXT';
  options?: string[];
  dangerAnswer?: string[];
};

export type SuggestedAction = {
  type: WorkflowActionType;
  label: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  confirmationRequired: boolean;
};
```

---

### 5.6 Workflow Action

```ts
export type WorkflowActionType =
  | 'CONFIRM_SAFE'
  | 'CALL_EMERGENCY'
  | 'ESCALATE'
  | 'CALL_ELDER'
  | 'CALL_SECOND_CONTACT'
  | 'REQUEST_HELP'
  | 'MARK_UNABLE_TO_CONFIRM'
  | 'ADD_NOTE';
```

---

## 6. API Endpoints

---

## 6.1 Dashboard API

### GET /api/caregiver/dashboard

Purpose:

Return dashboard summary and active alerts.

Request:

```http
GET /api/caregiver/dashboard
X-Caregiver-Id: 1001
```

Response:

```json
{
  "summary": {
    "normalCount": 3,
    "attentionCount": 1,
    "alertCount": 1
  },
  "activeEvents": [
    {
      "id": 501,
      "workflowId": 9001,
      "elder": {
        "id": 301,
        "name": "王美玉",
        "age": 82
      },
      "type": "FALL",
      "riskLevel": "HIGH",
      "status": "PROCESSING",
      "location": "客廳",
      "detectedAt": "2026-04-30T14:32:00+08:00",
      "sla": {
        "deadlineAt": "2026-04-30T14:33:00+08:00",
        "remainingSeconds": 18,
        "expired": false
      }
    }
  ],
  "recentTimeline": [
    {
      "time": "2026-04-30T14:32:00+08:00",
      "message": "系統偵測到王美玉疑似跌倒"
    }
  ]
}
```

Frontend behavior:

- Show red alert card when riskLevel is `HIGH` or `CRITICAL`.
- Show SLA countdown.
- Button `立即處理` navigates to `/caregiver/events/{eventId}`.

---

## 6.2 Event Detail API

### GET /api/care-events/{eventId}

Purpose:

Return event detail.

Response:

```json
{
  "id": 501,
  "elderId": 301,
  "workflowId": 9001,
  "type": "FALL",
  "riskLevel": "HIGH",
  "status": "PROCESSING",
  "location": "客廳",
  "detectedAt": "2026-04-30T14:32:00+08:00",
  "createdAt": "2026-04-30T14:32:01+08:00",
  "sensorSummary": {
    "noMovementSeconds": 120,
    "fallConfidence": 0.87,
    "source": "EDGE_GATEWAY"
  }
}
```

---

## 6.3 Elder Profile API

### GET /api/elders/{elderId}

Response:

```json
{
  "id": 301,
  "name": "王美玉",
  "age": 82,
  "mobility": "LOW",
  "chronicDiseases": ["diabetes", "hypertension"],
  "allergies": [],
  "address": "台北市大安區...",
  "emergencyNotes": "曾有跌倒紀錄，行動較慢"
}
```

---

## 6.4 Workflow API

### GET /api/workflows/{workflowId}

Purpose:

Return workflow state and current task.

Response:

```json
{
  "id": 9001,
  "eventId": 501,
  "elderId": 301,
  "workflowType": "FALL_RESPONSE",
  "riskLevel": "HIGH",
  "status": "WAITING_RESPONSE",
  "currentLevel": 1,
  "currentTask": {
    "id": 7001,
    "workflowId": 9001,
    "eventId": 501,
    "assigneeId": 1001,
    "assigneeName": "王先生",
    "assigneeType": "FAMILY",
    "level": 1,
    "status": "PENDING",
    "deadlineAt": "2026-04-30T14:33:00+08:00",
    "createdAt": "2026-04-30T14:32:01+08:00"
  },
  "startedAt": "2026-04-30T14:32:01+08:00",
  "createdAt": "2026-04-30T14:32:01+08:00"
}
```

Frontend behavior:

- Calculate countdown from `currentTask.deadlineAt`.
- If countdown is less than 10 seconds, visually emphasize urgency.
- If task expired, refresh workflow state.

---

## 6.5 AI Guidance API

### GET /api/ai/care-guidance

Request:

```http
GET /api/ai/care-guidance?eventId=501&workflowId=9001
X-Caregiver-Id: 1001
```

Response:

```json
{
  "summary": "目前偵測到高風險跌倒事件，長者已超過 120 秒無活動。",
  "guidance": [
    "請先確認長者是否清醒。",
    "不要急著扶起長者，先觀察是否有頭部撞擊、出血或劇烈疼痛。",
    "若長者意識不清、呼吸困難、胸痛、嚴重疼痛或疑似頭部撞擊，請立即撥打 119。"
  ],
  "questions": [
    {
      "id": "q_awake",
      "question": "長者現在清醒嗎？",
      "type": "YES_NO_UNKNOWN",
      "options": ["是", "否", "不確定"],
      "dangerAnswer": ["否", "不確定"]
    },
    {
      "id": "q_head_hit",
      "question": "是否有撞到頭？",
      "type": "YES_NO_UNKNOWN",
      "options": ["是", "否", "不確定"],
      "dangerAnswer": ["是", "不確定"]
    },
    {
      "id": "q_speech",
      "question": "是否能正常說話？",
      "type": "YES_NO_UNKNOWN",
      "options": ["是", "否", "不確定"],
      "dangerAnswer": ["否", "不確定"]
    }
  ],
  "suggestedActions": [
    {
      "type": "CALL_EMERGENCY",
      "label": "撥打 119",
      "priority": "HIGH",
      "confirmationRequired": true
    },
    {
      "type": "ESCALATE",
      "label": "通知第二聯絡人",
      "priority": "MEDIUM",
      "confirmationRequired": true
    },
    {
      "type": "CONFIRM_SAFE",
      "label": "我已確認安全",
      "priority": "MEDIUM",
      "confirmationRequired": true
    }
  ],
  "dangerSigns": [
    "意識不清",
    "呼吸困難",
    "頭部撞擊",
    "劇烈疼痛",
    "無法移動"
  ],
  "disclaimer": "此建議不能取代醫療診斷。若有危急狀況，請立即聯絡緊急服務。",
  "generatedAt": "2026-04-30T14:32:05+08:00"
}
```

Frontend behavior:

- Render `summary` at top of AI card.
- Render `guidance` as numbered steps.
- Render each `question` as large buttons.
- If user selects danger answer, highlight emergency action.
- Always keep `CALL_EMERGENCY` visible for high-risk events.

---

## 6.6 Submit Assessment Answers

### POST /api/workflows/{workflowId}/assessment-answers

Purpose:

Save caregiver answers to assessment questions.

Request:

```json
{
  "eventId": 501,
  "taskId": 7001,
  "answers": [
    {
      "questionId": "q_awake",
      "question": "長者現在清醒嗎？",
      "answer": "是"
    },
    {
      "questionId": "q_head_hit",
      "question": "是否有撞到頭？",
      "answer": "不確定"
    }
  ]
}
```

Response:

```json
{
  "workflowId": 9001,
  "taskId": 7001,
  "riskReevaluation": {
    "riskLevel": "HIGH",
    "dangerDetected": true,
    "recommendedAction": "CALL_EMERGENCY",
    "message": "因為頭部撞擊狀況不確定，建議提高警覺並考慮聯絡緊急服務。"
  },
  "saved": true
}
```

Backend requirements:

- Save answers.
- Write audit log.
- If danger answer exists, return `dangerDetected = true`.
- Do not auto-close workflow.

---

## 6.7 Take Workflow Action

### POST /api/workflows/{workflowId}/actions

Purpose:

Caregiver takes an action.

Request examples:

#### Confirm safe

```json
{
  "eventId": 501,
  "taskId": 7001,
  "actionType": "CONFIRM_SAFE",
  "note": "已電話確認，長者清醒且無明顯外傷。"
}
```

#### Escalate

```json
{
  "eventId": 501,
  "taskId": 7001,
  "actionType": "ESCALATE",
  "note": "無法立即到場，請第二聯絡人協助確認。"
}
```

#### Call emergency

```json
{
  "eventId": 501,
  "taskId": 7001,
  "actionType": "CALL_EMERGENCY",
  "note": "已撥打119，長者疑似跌倒且頭部撞擊不確定。"
}
```

Response:

```json
{
  "workflowId": 9001,
  "eventId": 501,
  "taskId": 7001,
  "actionType": "CONFIRM_SAFE",
  "workflowStatus": "RESOLVED",
  "taskStatus": "COMPLETED",
  "message": "事件已更新",
  "timeline": [
    {
      "time": "2026-04-30T14:32:00+08:00",
      "type": "EVENT_CREATED",
      "message": "系統偵測疑似跌倒"
    },
    {
      "time": "2026-04-30T14:34:00+08:00",
      "type": "ACTION_RECEIVED",
      "message": "王先生已確認安全"
    }
  ]
}
```

Backend behavior:

- Validate task is still actionable.
- Use conditional update to avoid duplicate actions.
- Save action.
- Save audit log.
- Update workflow status.
- Return updated timeline.

Action handling rules:

```text
CONFIRM_SAFE:
  - task.status = COMPLETED
  - workflow.status = RESOLVED
  - event.status = RESOLVED

ESCALATE:
  - current task.status = ESCALATED
  - create next-level task
  - workflow.status = ESCALATED or WAITING_RESPONSE
  - notify next contact

CALL_EMERGENCY:
  - task.status = ACKNOWLEDGED or COMPLETED depending on policy
  - workflow.status = ACTIVE or RESOLVED only after caregiver confirms emergency was contacted
  - audit must record this action

MARK_UNABLE_TO_CONFIRM:
  - task.status = ESCALATED
  - create next-level task
```

---

## 6.8 Timeline API

### GET /api/workflows/{workflowId}/timeline

Response:

```json
{
  "workflowId": 9001,
  "items": [
    {
      "id": 1,
      "time": "2026-04-30T14:32:00+08:00",
      "type": "EVENT_CREATED",
      "actorName": "System",
      "message": "系統偵測王美玉疑似跌倒",
      "level": "INFO"
    },
    {
      "id": 2,
      "time": "2026-04-30T14:32:01+08:00",
      "type": "TASK_CREATED",
      "actorName": "System",
      "message": "已通知主要照顧者：王先生",
      "level": "INFO"
    },
    {
      "id": 3,
      "time": "2026-04-30T14:33:00+08:00",
      "type": "TASK_TIMEOUT",
      "actorName": "System",
      "message": "主要照顧者未在 SLA 內回應",
      "level": "WARNING"
    }
  ]
}
```

Frontend behavior:

- Display timeline in chronological order.
- Use green for completed, yellow for warnings, red for critical.
- Do not expose internal IDs.

---

## 6.9 Contacts API

### GET /api/elders/{elderId}/contacts

Response:

```json
{
  "elderId": 301,
  "contacts": [
    {
      "id": 1001,
      "name": "王先生",
      "relationship": "兒子",
      "phone": "+886912345678",
      "priorityLevel": 1
    },
    {
      "id": 1002,
      "name": "陳小姐",
      "relationship": "女兒",
      "phone": "+886923456789",
      "priorityLevel": 2
    }
  ]
}
```

---

## 7. Frontend Component Design

### 7.1 Component List

```text
DashboardPage
ActiveEventCard
EventDetailPage
EventHeaderCard
SlaCountdownBar
AiGuidanceCard
AssessmentQuestionCard
ActionButtonBar
ActionConfirmationModal
TimelinePanel
CareProfileCard
EmergencyCallModal
```

---

### 7.2 SlaCountdownBar

Props:

```ts
type SlaCountdownBarProps = {
  deadlineAt: string;
  status: TaskStatus;
  onExpired?: () => void;
};
```

Behavior:

```text
- Recalculate remaining seconds every second.
- If remaining <= 0, show expired state.
- Trigger onExpired once.
- UI states:
  - > 30s: normal
  - <= 30s: warning
  - <= 10s: urgent
  - expired: expired
```

---

### 7.3 AiGuidanceCard

Props:

```ts
type AiGuidanceCardProps = {
  guidance: AICareGuidance;
  onAnswer: (questionId: string, answer: string) => void;
};
```

Behavior:

```text
- Show summary.
- Show guidance steps.
- Render questions as large answer buttons.
- Highlight danger answers.
- Do not render free-text chat in MVP.
```

---

### 7.4 ActionButtonBar

Props:

```ts
type ActionButtonBarProps = {
  actions: SuggestedAction[];
  onActionClick: (actionType: WorkflowActionType) => void;
};
```

Behavior:

```text
- Fixed at bottom on mobile.
- For HIGH / CRITICAL event, CALL_EMERGENCY appears first.
- CONFIRM_SAFE always requires confirmation modal.
```

---

### 7.5 ActionConfirmationModal

Confirm safe message:

```text
請確認：
你是否已親自確認長者安全？
此操作會記錄在照護責任鏈中。

[確認安全] [返回]
```

Escalate message:

```text
系統將通知第二順位照顧者。
此操作會記錄在照護責任鏈中。

[確認通知] [返回]
```

Emergency message:

```text
請立即撥打 119。
建議告知：
- 長者姓名
- 地址
- 疑似跌倒
- 是否清醒
- 是否有頭部撞擊

[撥打119] [已撥打，記錄事件]
```

---

## 8. Backend Implementation Requirements

### 8.1 Service Modules

```text
aethercare-gateway
aethercare-user-service
aethercare-event-service
aethercare-workflow-service
aethercare-ai-assistant-service
aethercare-notification-service
aethercare-audit-service
```

For MVP, these may be implemented as packages inside one Spring Boot application:

```text
com.aethercare.event
com.aethercare.workflow
com.aethercare.ai
com.aethercare.notification
com.aethercare.audit
com.aethercare.user
```

---

### 8.2 Backend Package Structure

```text
src/main/java/com/aethercare
  /api
    CareEventController.java
    WorkflowController.java
    AiAssistantController.java
    ElderController.java
    DashboardController.java
  /domain
    CareEvent.java
    WorkflowInstance.java
    CareTask.java
    CareAction.java
    AuditLog.java
    ElderProfile.java
  /service
    CareEventService.java
    WorkflowService.java
    SlaService.java
    EscalationService.java
    AiAssistantService.java
    AuditService.java
    NotificationService.java
  /repository
    CareEventRepository.java
    WorkflowRepository.java
    CareTaskRepository.java
    CareActionRepository.java
    AuditLogRepository.java
  /dto
    request
    response
  /config
  /common
```

---

### 8.3 Database Tables

#### care_event

```sql
CREATE TABLE care_event (
  id BIGINT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  workflow_id BIGINT NULL,
  type VARCHAR(50) NOT NULL,
  risk_level VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  location VARCHAR(255),
  detected_at TIMESTAMP NOT NULL,
  sensor_summary JSON NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

#### care_workflow_instance

```sql
CREATE TABLE care_workflow_instance (
  id BIGINT PRIMARY KEY,
  event_id BIGINT NOT NULL,
  elder_id BIGINT NOT NULL,
  workflow_type VARCHAR(50) NOT NULL,
  risk_level VARCHAR(20) NOT NULL,
  status VARCHAR(30) NOT NULL,
  current_level INT NOT NULL DEFAULT 1,
  started_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version INT NOT NULL DEFAULT 0
);
```

#### care_task

```sql
CREATE TABLE care_task (
  id BIGINT PRIMARY KEY,
  workflow_id BIGINT NOT NULL,
  event_id BIGINT NOT NULL,
  assignee_id BIGINT NOT NULL,
  assignee_name VARCHAR(100) NOT NULL,
  assignee_type VARCHAR(30) NOT NULL,
  level INT NOT NULL,
  status VARCHAR(30) NOT NULL,
  deadline_at TIMESTAMP NOT NULL,
  acknowledged_at TIMESTAMP NULL,
  completed_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  version INT NOT NULL DEFAULT 0
);
```

#### care_action

```sql
CREATE TABLE care_action (
  id BIGINT PRIMARY KEY,
  workflow_id BIGINT NOT NULL,
  event_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  actor_id BIGINT NOT NULL,
  actor_name VARCHAR(100) NOT NULL,
  action_type VARCHAR(50) NOT NULL,
  note TEXT NULL,
  created_at TIMESTAMP NOT NULL
);
```

#### care_assessment_answer

```sql
CREATE TABLE care_assessment_answer (
  id BIGINT PRIMARY KEY,
  workflow_id BIGINT NOT NULL,
  event_id BIGINT NOT NULL,
  task_id BIGINT NOT NULL,
  caregiver_id BIGINT NOT NULL,
  question_id VARCHAR(100) NOT NULL,
  question TEXT NOT NULL,
  answer VARCHAR(255) NOT NULL,
  danger_detected BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL
);
```

#### care_audit_log

```sql
CREATE TABLE care_audit_log (
  id BIGINT PRIMARY KEY,
  workflow_id BIGINT NOT NULL,
  event_id BIGINT NOT NULL,
  actor_id BIGINT NULL,
  actor_name VARCHAR(100) NULL,
  type VARCHAR(100) NOT NULL,
  message TEXT NOT NULL,
  level VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL
);
```

#### elder_profile

```sql
CREATE TABLE elder_profile (
  id BIGINT PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  age INT NOT NULL,
  mobility VARCHAR(20) NOT NULL,
  chronic_diseases JSON NULL,
  allergies JSON NULL,
  address VARCHAR(255),
  emergency_notes TEXT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

#### elder_contact

```sql
CREATE TABLE elder_contact (
  id BIGINT PRIMARY KEY,
  elder_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  relationship VARCHAR(50) NOT NULL,
  phone VARCHAR(50) NOT NULL,
  priority_level INT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

---

## 9. Workflow Logic

### 9.1 Start Workflow

Pseudo code:

```java
public WorkflowInstance startWorkflow(CareEvent event) {
    WorkflowInstance workflow = workflowRepository.create(event);
    Contact levelOneContact = contactService.getContact(event.getElderId(), 1);

    CareTask task = taskRepository.create(
        workflow.getId(),
        event.getId(),
        levelOneContact,
        1,
        now().plusSeconds(resolveSlaSeconds(event.getRiskLevel(), 1))
    );

    notificationService.notifyCaregiver(task);
    auditService.log(workflow, event, "TASK_CREATED", "已通知主要照顧者：" + levelOneContact.getName());

    return workflow;
}
```

---

### 9.2 Timeout Scanner

MVP can use scheduled scan every 5 seconds.

```java
@Scheduled(fixedDelay = 5000)
public void scanTimeoutTasks() {
    List<CareTask> tasks = taskRepository.findExpiredPendingTasks(now());
    for (CareTask task : tasks) {
        boolean locked = taskRepository.markTimeoutIfPending(task.getId(), now());
        if (locked) {
            escalationService.escalate(task);
        }
    }
}
```

Important conditional update:

```sql
UPDATE care_task
SET status = 'TIMEOUT', updated_at = NOW(), version = version + 1
WHERE id = :taskId
AND status = 'PENDING'
AND deadline_at < NOW();
```

Only escalate if affected rows = 1.

---

### 9.3 Escalation

```java
public void escalate(CareTask expiredTask) {
    WorkflowInstance workflow = workflowRepository.findById(expiredTask.getWorkflowId());
    int nextLevel = expiredTask.getLevel() + 1;

    Contact nextContact = contactService.getContact(workflow.getElderId(), nextLevel);
    if (nextContact == null) {
        workflowRepository.markFailed(workflow.getId());
        auditService.log(workflow, "ESCALATION_FAILED", "沒有下一順位聯絡人");
        return;
    }

    CareTask nextTask = taskRepository.create(
        workflow.getId(),
        workflow.getEventId(),
        nextContact,
        nextLevel,
        now().plusSeconds(resolveSlaSeconds(workflow.getRiskLevel(), nextLevel))
    );

    workflowRepository.updateCurrentLevel(workflow.getId(), nextLevel);
    notificationService.notifyCaregiver(nextTask);
    auditService.log(workflow, "ESCALATED", "已升級通知：" + nextContact.getName());
}
```

---

## 10. AI Assistant Implementation

### 10.1 Safety Rules

AI must never:

```text
- Diagnose disease
- Say the elder is definitely safe
- Tell user to delay emergency care for high-risk symptoms
- Close workflow automatically
```

AI must always:

```text
- Provide clear next steps
- Ask assessment questions
- Suggest emergency action for danger signs
- Return structured JSON
- Include disclaimer
```

---

### 10.2 AI System Prompt

```text
You are AetherCare AI Care Assistant.

Your role is to guide caregivers during home care situations.

Rules:
1. You are not a doctor. Do not provide medical diagnosis.
2. Prioritize safety. If there are any danger signs, recommend emergency services immediately.
3. Base your response only on the given event context and care knowledge.
4. Provide clear situation summary, step-by-step guidance, assessment questions, and suggested actions.
5. Never say "it is safe" or "no need to worry".
6. When uncertain, escalate rather than reassure.
7. Output valid JSON only.
```

---

### 10.3 AI Context Builder Input

```json
{
  "event": {
    "type": "FALL",
    "riskLevel": "HIGH",
    "location": "客廳",
    "sensorSummary": {
      "noMovementSeconds": 120,
      "fallConfidence": 0.87
    }
  },
  "elderProfile": {
    "age": 82,
    "mobility": "LOW",
    "chronicDiseases": ["diabetes", "hypertension"],
    "emergencyNotes": "曾有跌倒紀錄，行動較慢"
  },
  "workflow": {
    "status": "WAITING_RESPONSE",
    "currentLevel": 1,
    "deadlineAt": "2026-04-30T14:33:00+08:00"
  },
  "knowledgeChunks": [
    {
      "eventType": "FALL",
      "content": "跌倒後不要立即扶起，先確認意識、呼吸、頭部撞擊與出血。若意識不清、呼吸困難或疑似頭部撞擊，應立即聯絡緊急服務。"
    }
  ]
}
```

---

### 10.4 Knowledge Base MVP

Create file:

```text
src/main/resources/care-knowledge/fall.json
src/main/resources/care-knowledge/no_activity.json
src/main/resources/care-knowledge/stroke.json
src/main/resources/care-knowledge/wandering.json
src/main/resources/care-knowledge/breathing_issue.json
```

Example `fall.json`:

```json
{
  "eventType": "FALL",
  "riskLevel": "HIGH",
  "summary": "疑似跌倒事件需要先確認意識、呼吸、頭部撞擊與疼痛情況。",
  "guidance": [
    "不要立即扶起長者。",
    "先確認長者是否清醒與是否能正常呼吸。",
    "觀察是否有頭部撞擊、出血、劇烈疼痛或無法移動。",
    "若有危險徵象，請立即撥打119。"
  ],
  "questions": [
    {
      "id": "q_awake",
      "question": "長者現在清醒嗎？",
      "type": "YES_NO_UNKNOWN",
      "options": ["是", "否", "不確定"],
      "dangerAnswer": ["否", "不確定"]
    },
    {
      "id": "q_head_hit",
      "question": "是否有撞到頭？",
      "type": "YES_NO_UNKNOWN",
      "options": ["是", "否", "不確定"],
      "dangerAnswer": ["是", "不確定"]
    }
  ],
  "dangerSigns": [
    "意識不清",
    "呼吸困難",
    "頭部撞擊",
    "劇烈疼痛",
    "無法移動"
  ],
  "suggestedActions": [
    {
      "type": "CALL_EMERGENCY",
      "label": "撥打119",
      "priority": "HIGH",
      "confirmationRequired": true
    },
    {
      "type": "ESCALATE",
      "label": "通知第二聯絡人",
      "priority": "MEDIUM",
      "confirmationRequired": true
    },
    {
      "type": "CONFIRM_SAFE",
      "label": "我已確認安全",
      "priority": "MEDIUM",
      "confirmationRequired": true
    }
  ],
  "disclaimer": "此建議不能取代醫療診斷。若有危急狀況，請立即聯絡緊急服務。"
}
```

MVP may return this static knowledge first before integrating LLM.

---

## 11. Error Handling

### Standard Error Response

```json
{
  "errorCode": "WORKFLOW_TASK_NOT_ACTIONABLE",
  "message": "此任務已被處理或已超時",
  "traceId": "abc-123"
}
```

### Error Codes

```text
EVENT_NOT_FOUND
WORKFLOW_NOT_FOUND
TASK_NOT_FOUND
WORKFLOW_TASK_NOT_ACTIONABLE
ACTION_NOT_ALLOWED
AI_GUIDANCE_UNAVAILABLE
CONTACT_NOT_FOUND
SLA_EXPIRED
UNAUTHORIZED
VALIDATION_ERROR
```

Frontend behavior:

```text
- If WORKFLOW_TASK_NOT_ACTIONABLE: refresh workflow and timeline.
- If AI_GUIDANCE_UNAVAILABLE: show fallback static guidance.
- If SLA_EXPIRED: show escalation status and refresh workflow.
```

---

## 12. Mock Data for MVP

### Elder

```json
{
  "id": 301,
  "name": "王美玉",
  "age": 82,
  "mobility": "LOW",
  "chronicDiseases": ["diabetes", "hypertension"],
  "address": "台北市大安區..."
}
```

### Primary Caregiver

```json
{
  "id": 1001,
  "name": "王先生",
  "relationship": "兒子",
  "phone": "+886912345678",
  "priorityLevel": 1
}
```

### Fall Event

```json
{
  "id": 501,
  "elderId": 301,
  "workflowId": 9001,
  "type": "FALL",
  "riskLevel": "HIGH",
  "status": "PROCESSING",
  "location": "客廳",
  "detectedAt": "2026-04-30T14:32:00+08:00",
  "sensorSummary": {
    "noMovementSeconds": 120,
    "fallConfidence": 0.87,
    "source": "EDGE_GATEWAY"
  }
}
```

---

## 13. Claude Code Implementation Tasks

### Phase 1 — Backend MVP

```text
1. Create Spring Boot project structure.
2. Add domain entities and DTOs.
3. Implement mock repositories or JPA repositories.
4. Implement DashboardController.
5. Implement CareEventController.
6. Implement WorkflowController.
7. Implement AiAssistantController with static knowledge JSON response.
8. Implement Audit timeline API.
9. Implement action submission and workflow status update.
10. Implement basic timeout scanner.
```

Acceptance criteria:

```text
- GET dashboard returns active fall event.
- GET event detail returns event + workflow ID.
- GET AI guidance returns structured guidance.
- POST assessment answers saves answers and returns risk reevaluation.
- POST action CONFIRM_SAFE resolves workflow.
- GET timeline shows all actions.
```

---

### Phase 2 — Frontend MVP

```text
1. Create Next.js / React project.
2. Build API client.
3. Build DashboardPage.
4. Build ActiveEventCard.
5. Build EventDetailPage.
6. Build SlaCountdownBar.
7. Build AiGuidanceCard.
8. Build AssessmentQuestionCard.
9. Build ActionButtonBar.
10. Build ActionConfirmationModal.
11. Build TimelinePanel.
12. Wire all APIs.
```

Acceptance criteria:

```text
- Dashboard shows active high-risk event.
- Clicking event opens Event Detail.
- Event Detail shows AI guidance.
- User can answer questions.
- User can confirm safe.
- UI updates to resolved status.
- Timeline shows 王先生 as handler.
```

---

### Phase 3 — AI Integration

```text
1. Add AI context builder.
2. Add care knowledge loader.
3. Add LLM provider abstraction.
4. Add safety filter.
5. Add structured JSON validation.
6. Add fallback to static guidance if AI fails.
```

Acceptance criteria:

```text
- AI response must be valid JSON.
- AI response must include disclaimer.
- AI response must not diagnose.
- High-risk events must include emergency action.
```

---

## 14. UX Rules

```text
1. High-risk event pages must always show emergency action.
2. Confirm safe must always require confirmation.
3. SLA countdown must be visible above the fold.
4. Use large buttons for assessment answers.
5. Avoid free text during emergency flow.
6. Timeline must clearly show who did what and when.
7. AI must guide actions, not replace human responsibility.
```

---

## 15. Security and Privacy Notes

MVP minimum:

```text
- Do not expose internal IDs in visible UI.
- Mask phone numbers unless user taps call action.
- Log access to event detail.
- Log AI guidance generation.
- Store only necessary health-related data.
```

Future:

```text
- JWT auth
- Role-based access control
- Consent management
- Data retention policy
- Encryption at rest
- Audit export
```

---

## 16. Definition of Done

The MVP is done when:

```text
1. A mock fall event appears on dashboard.
2. 王先生 can open the event.
3. AI guidance appears.
4. 王先生 answers assessment questions.
5. 王先生 selects one action.
6. Workflow status changes correctly.
7. Timeline records the full responsibility chain.
8. Claude Code can run backend and frontend locally.
```

---

## 17. Local Development Suggestion

Backend:

```bash
./mvnw spring-boot:run
```

Frontend:

```bash
npm install
npm run dev
```

Environment variables:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
AETHERCARE_MOCK_MODE=true
```

---

## 18. Final Product Statement

AetherCare is not only a notification system.

AetherCare is a Care Execution System:

```text
Detect -> Decide -> Guide -> Act -> Escalate -> Audit
```
