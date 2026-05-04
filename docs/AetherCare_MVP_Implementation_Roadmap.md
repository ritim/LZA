# AetherCare MVP Implementation Roadmap

This roadmap translates the master spec into buildable phases.

## Phase 0: Create the Baseline

Goal: create a decision-complete implementation baseline before feature work.

Tasks:

- Create a monorepo folder `aethercare-mvp/`.
- Create `aethercare-mvp/backend/` as a Spring Boot 3.x Java 21 Maven project.
- Create `aethercare-mvp/frontend/` as a Next.js TypeScript Tailwind project.
- Use `care_events`, never `anomaly_events`.
- Use `/api/v1/care-recipients`, never `/api/v1/elders`.
- Use mock identity headers: `X-Caregiver-Id`, `X-Care-Recipient-Id`.
- Seed one demo household, one passive care recipient, and two caregivers.
- Seed observation settings for missed check-in and no-activity thresholds.
- Add static AI guidance JSON files before any LLM integration.

Acceptance:

- Backend and frontend run locally.
- One naming glossary exists in code comments or README.
- All API examples use the same IDs and event names.

## Phase 1: Backend Core

Goal: build the event-to-workflow loop.

Backend modules:

```text
recipient
activity
care-event
workflow
notification
ai-guidance
audit
dashboard
```

Minimum endpoints:

```http
POST /api/v1/recipient/check-ins
POST /api/v1/recipient/sos
POST /api/v1/recipient/status-reports
GET  /api/v1/caregiver/dashboard
GET  /api/v1/care-events/{eventId}
GET  /api/v1/workflows/{workflowId}
GET  /api/v1/workflows/{workflowId}/timeline
POST /api/v1/workflows/{workflowId}/actions
GET  /api/v1/ai/care-guidance
```

Rules:

- `CHECK_IN` updates status and does not create workflow by default.
- `SOS_PRESSED` creates HIGH event and workflow.
- `MISSED_CHECK_IN` creates MEDIUM event and workflow.
- `NO_ACTIVITY` creates MEDIUM or HIGH event depending on profile and duration.
- `CALL_NO_ANSWER` never resolves workflow.
- `CONFIRM_SAFE` resolves workflow and event.
- Timeout scanner escalates pending tasks using conditional update.

Acceptance:

- Backend can run full canonical demo flow using API calls.
- Timeline records every state change.
- Duplicate timeout workers cannot create duplicate level 2 tasks.

## Phase 2: Static AI Guidance and AI Care Chat

Goal: provide useful event-bound AI support without depending on LLM availability.

Create static knowledge records for:

```text
fall
possible_fall
no_activity
missed_check_in
no_response
feeling_unwell
```

Each guidance response includes:

```text
summary
guidance[]
questions[]
suggestedActions[]
dangerSigns[]
disclaimer
generatedAt
```

Acceptance:

- NO_RESPONSE guidance does not reassure.
- NO_ACTIVITY guidance asks about last activity, phone contact, on-site availability, and fall risk.
- Static guidance appears without any LLM configuration.
- `POST /api/v1/ai/care-chat` returns structured guidance and suggested actions.
- AI Care Chat persists caregiver and assistant messages.
- AI Care Chat suggestions do not mutate workflow state without explicit caregiver action confirmation.

## Phase 3: Recipient Web App

Goal: let active or partially active recipients be heard.

Screens:

- Home with four large actions.
- Check-in confirmation.
- SOS confirmation with cancel grace period if appropriate.
- Feeling unwell simple question flow.

UX rules:

- Large touch targets.
- High contrast.
- No dense navigation.
- No hidden critical actions.
- Text must fit on mobile.

Acceptance:

- Pressing check-in updates caregiver dashboard.
- Pressing SOS creates active event on caregiver dashboard.
- Feeling unwell produces a status report and can create event if high risk.

## Phase 4: Caregiver Web App

Goal: guide caregivers under pressure.

Screens:

- Dashboard.
- Event detail.
- Care profile.
- Timeline component.
- Action confirmation modal.

Event detail must show above the fold:

```text
risk level
what happened
who is affected
SLA countdown
current task owner
primary action buttons
```

Action buttons:

```text
確認安全
打電話
電話未接
通知下一位
請人到場確認
撥打119
新增備註
```

Acceptance:

- `電話未接` updates timeline but keeps workflow open.
- Expired task refreshes workflow and shows escalation state.
- Danger answers highlight emergency and escalation actions.

## Phase 5: Notification and Escalation

Goal: prove shared responsibility.

MVP notification is mock only, but must persist records.

Rules:

- Level 1 receives first task.
- Timeout creates level 2 task.
- If no next caregiver, workflow becomes `UNRESOLVED`.
- Notification failure is recorded and shown in timeline.

Acceptance:

- Demo can show timeout escalation without manual database edits.
- Timeline shows notification sent or failed.

## Phase 6: Demo Hardening

Goal: make the MVP pilot/募資 demo reliable.

Canonical demo:

```text
王美玉, 82, PASSIVE, low mobility
09:00 expected check-in
10:00 missed check-in event
primary caregiver notified
primary caregiver records call no answer
AI guidance suggests escalation/on-site check
SLA timeout escalates to second caregiver
second caregiver confirms safe
workflow resolved
timeline complete
```

Acceptance checklist:

- No event can disappear without timeline.
- No unresolved event can look resolved.
- AI failure never blocks action buttons.
- SLA countdown works on desktop and mobile.
- All demo timestamps are Asia/Taipei.
- Dashboard and event detail use the same event status.

## Phase 7: Post-MVP

After the first demo works:

- Add LINE Messaging API or SMS integration.
- Add LLM guidance behind static fallback.
- Add caregiver stress/support metrics.
- Add recipient consent and data export.
- Add optional sensor ingestion.
- Add institution/team roles.
- Add SLA dashboard for response time, timeout rate, and escalation rate.
