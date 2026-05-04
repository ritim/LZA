# Claude Code Instructions for LZA / AetherCare

You are implementing AetherCare MVP for LZA.

## Read Order

Read these files first, in this order:

1. `docs/AetherCare_MVP_Master_Spec.md`
2. `docs/AetherCare_AI_Care_Chat_Spec.md`
3. `docs/AetherCare_MVP_Implementation_Roadmap.md`
4. `AetherCare_PostgreSQL_Schema.sql`
5. `docs/AetherCare_MVP_Gap_Review.md`

Older files are reference only:

- `aethercare_codex_system_design.md`
- `aethercare_frontend_backend_api_integration.md`

If any older file conflicts with the master spec, follow `docs/AetherCare_MVP_Master_Spec.md`.

## Product Goal

Build an MVP that proves:

```text
care signal
→ care event
→ workflow
→ caregiver task
→ notification record
→ caregiver action or SLA timeout
→ escalation or resolution
→ audit timeline
```

The core principle is:

> Silence is a signal. No response is not proof of safety.

## Fixed Implementation Decisions

Do not re-decide these:

- Build in `aethercare-mvp/`.
- Backend: `aethercare-mvp/backend`, Java 21, Spring Boot 3.x, Maven, PostgreSQL.
- Frontend: `aethercare-mvp/frontend`, Next.js, TypeScript, Tailwind CSS, TanStack Query.
- Java base package: `com.lza.aethercare`.
- Backend DB migration: use Flyway. Convert `AetherCare_PostgreSQL_Schema.sql` into `backend/src/main/resources/db/migration/V1__init_schema.sql`.
- Local DB: add Docker Compose PostgreSQL 16 in `aethercare-mvp/docker-compose.yml`.
- MVP architecture: modular monolith. Do not create microservices.
- MVP event bus: none. Do not use Kafka.
- MVP notification: mock service that persists `notification_records`.
- MVP AI: static JSON care guidance files. Do not require an LLM key.
- MVP AI chat: event-bound AI Care Chat only. Do not build a standalone chatbot.
- MVP auth: mock headers `X-Caregiver-Id` and `X-Care-Recipient-Id`.
- Canonical database table: `care_events`. Do not create `anomaly_events`.
- Canonical resource: `/api/v1/care-recipients`. Do not create `/api/v1/elders`.
- Route namespace rule: `/api/v1/recipient/*` is for recipient self-actions; `/api/v1/care-recipients/*` is for caregiver/admin reads and settings.

## Required MVP Backend Behavior

Implement these behaviors before polishing UI:

- `CHECK_IN` creates an `activity_logs` row and updates dashboard status. It does not create a workflow by default.
- `SOS_PRESSED` creates a HIGH `care_events` row, starts workflow, creates level 1 task, writes audit, and creates mock notification.
- `MISSED_CHECK_IN` creates a MEDIUM `care_events` row and starts workflow.
- `NO_ACTIVITY` creates MEDIUM or HIGH event based on duration and recipient profile.
- `CALL_NO_ANSWER` writes `care_actions` and `care_audit_logs`, but keeps workflow open.
- `CONFIRM_SAFE` completes the current task, resolves workflow, resolves care event, and writes audit.
- SLA timeout scanner marks only still-pending expired tasks as `TIMEOUT`, then creates exactly one next-level task.
- If no next caregiver exists, workflow becomes `UNRESOLVED`.

Use these backend package areas:

```text
com.lza.aethercare.recipient
com.lza.aethercare.activity
com.lza.aethercare.careevent
com.lza.aethercare.workflow
com.lza.aethercare.notification
com.lza.aethercare.aiguidance
com.lza.aethercare.aichat
com.lza.aethercare.audit
com.lza.aethercare.dashboard
com.lza.aethercare.common
```

## Required MVP Frontend Behavior

Recipient app:

- Four primary actions: `我今天還好`, `我需要幫忙`, `身體不舒服`, `打給家人`.
- Large touch targets, high contrast, minimal navigation.

Caregiver app:

- Dashboard shows active events, waiting tasks, SLA countdowns, latest check-in/activity, and resolved-today count.
- Event detail shows risk, recipient, what happened, SLA countdown, AI/static guidance, questions, action buttons, and timeline.
- Event detail includes AI Care Chat panel bound to the current event and workflow.
- `電話未接` must visibly keep the event open.
- Expired tasks must refresh to show escalation.

Use these frontend routes:

```text
/recipient
/caregiver/dashboard
/caregiver/events/[eventId]
/caregiver/recipients/[careRecipientId]
```

## Static Guidance Files

Create backend resource files for:

```text
fall.json
possible_fall.json
no_activity.json
missed_check_in.json
no_response.json
feeling_unwell.json
```

Each guidance response must include:

```text
summary
guidance[]
questions[]
suggestedActions[]
dangerSigns[]
disclaimer
generatedAt
```

AI/static guidance must never say the recipient is definitely safe, and must never close workflow.

## Canonical Demo Data

Seed this demo:

```text
Care recipient: 王美玉, 82, PASSIVE, low mobility
Primary caregiver: 王先生
Second caregiver: 陳小姐
Expected check-in: 09:00 Asia/Taipei
Grace period: 60 minutes
Canonical event: 10:00 missed check-in → CALL_NO_ANSWER → timeout escalation → second caregiver CONFIRM_SAFE
```

## Required Tests

Backend tests:

- Check-in does not create workflow.
- SOS creates event, workflow, task, notification, and audit.
- Missed check-in creates workflow for PASSIVE recipient.
- `CALL_NO_ANSWER` does not resolve workflow.
- SLA timeout creates one next-level task only.
- `CONFIRM_SAFE` resolves task, workflow, and care event.
- Static guidance returns valid structured JSON for `NO_RESPONSE` and `NO_ACTIVITY`.
- AI Care Chat saves user and assistant messages.
- AI Care Chat suggested actions do not change workflow state until caregiver confirms an action button.

Frontend tests or manual verification:

- Recipient check-in updates caregiver dashboard.
- SOS appears as active event.
- Event detail shows guidance and timeline.
- `電話未接` keeps workflow open.
- SLA expiration displays escalation state.

## Stop Conditions

Do not spend time on these unless explicitly asked:

- Real LINE/SMS provider integration.
- Real LLM integration.
- Standalone general-purpose chatbot.
- Robot hardware.
- Kafka or microservices.
- Institution-grade RBAC.
- Sensor/video ingestion.
