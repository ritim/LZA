# AetherCare MVP Gap Review

Date: 2026-05-01  
Scope: Review of current `LZA.md`, system design, API integration spec, SQL schema, and landing page.

## 1. What Is Already Strong

- The core product direction is clear: AetherCare is a care execution platform, not just monitoring.
- The strongest MVP proof point is correct: event → task → notification → response → escalation → audit timeline.
- The "silence is a signal" principle now covers people who cannot actively express needs.
- AI safety boundaries are mostly clear: guide actions, do not diagnose, do not auto-close workflow.
- PostgreSQL schema already has core household, recipient, activity, event, notification, AI advice, workflow/task/action/audit concepts.

## 2. Gaps Found and Decisions

### Gap A: Naming is inconsistent

Current documents mix:

```text
elder / care recipient
care_event / anomaly_events
care_workflow_instance / care_workflow_instances
care_task / care_tasks
care_audit_log / care_audit_logs
```

Decision:

- Product language uses `care recipient`, not `elder`.
- Product/API language uses `care event`.
- Implementation must use `care_events`, not `anomaly_events`.
- Do not create `/elders` APIs in new code.
- Tables are plural snake_case: `care_events`, `care_workflow_instances`, `care_tasks`, `care_actions`, `care_audit_logs`.
- API resources are plural and versioned: `/api/v1/care-events`, `/api/v1/workflows`, `/api/v1/care-recipients`.
- Code types are singular PascalCase: `CareEvent`, `WorkflowInstance`, `CareTask`, `CareAction`, `CareAuditLog`.

### Gap B: Recipient-side product was under-specified

Older docs focused on caregiver UI. MVP now needs recipient UI as a first-class surface.

Decision:

- Build recipient mobile Web App first.
- Must support `CHECK_IN`, `SOS_PRESSED`, `FEELING_UNWELL`.
- Passive recipients are still supported through missed check-ins, no activity, phone last-seen, and caregiver manual reports.

### Gap C: Passive monitoring settings need APIs

Schema now includes `recipient_observation_settings`, but API specs do not yet fully define CRUD.

Decision:

Implement these in Phase 1 or seed-only if UI time is short:

```http
GET  /api/care-recipients/{id}/observation-settings
PUT  /api/care-recipients/{id}/observation-settings
```

MVP must seed these settings. The API should exist even if the UI does not expose settings yet.

### Gap D: Action policy needs hard rules

Some caregiver actions are final; others are only evidence.

Decision:

Actions that can resolve workflow:

```text
CONFIRM_SAFE
CALL_EMERGENCY only after caregiver confirms emergency handoff policy
```

Actions that must keep workflow open:

```text
CALL_NO_ANSWER
MARK_UNABLE_TO_CONFIRM
REQUEST_ON_SITE_CHECK
ADD_NOTE
```

Actions that create or trigger escalation:

```text
ESCALATE
CALL_SECOND_CONTACT
REQUEST_HELP
SLA timeout
```

### Gap E: Privacy and consent are missing

Home care data is sensitive. Current docs do not yet define privacy defaults.

Decision:

MVP must include at least:

- Household-level access boundary.
- Caregiver membership and relationship.
- Audit log visible to authorized caregivers only.
- No raw sensor/video requirement in MVP.
- Clear statement that AI suggestions are not medical diagnosis.

Post-MVP:

- Consent management.
- Data retention policy.
- Export/delete request process.
- Institution-level roles.

### Gap F: Notification reliability is under-defined

Notification records exist, but retry, fallback channel, and delivery acknowledgement are not fully specified.

Decision:

MVP uses mock notification only, but must still record:

```text
channel
target_user_id
delivery_status
sent_at
failure_reason
```

Post-MVP production rules:

- Retry failed notification.
- Fallback from push to LINE/SMS.
- Escalate if notification cannot be delivered.

### Gap G: AI knowledge base files are not present

The API spec names knowledge files, but no actual JSON files exist yet.

Decision:

Implement static guidance files before any LLM integration:

```text
fall
no_activity
missed_check_in
no_response
feeling_unwell
```

This is enough for a high-quality MVP demo.

### Gap H: Dashboard metrics are not defined

The product should prove care was caught, not only display alerts.

Decision:

MVP dashboard should expose:

```text
activeEventsCount
waitingResponseCount
expiredTaskCount
resolvedTodayCount
latestCheckInAt
latestActivityAt
nextEscalationDeadline
```

### Gap I: Demo data needs one canonical story

Existing docs include examples, but no single demo script.

Decision:

Use this canonical MVP demo:

```text
王美玉，82歲，低行動能力，PASSIVE mode
平常 09:00 check-in，grace 60 min
10:00 no check-in → MISSED_CHECK_IN event
primary caregiver receives task
primary caregiver calls, no answer → CALL_NO_ANSWER
AI recommends next contact or on-site check
SLA expires → level 2 notified
level 2 confirms safe → workflow resolved
timeline shows every step
```

## 3. Docs Consolidation Decision

New canonical docs:

- `docs/AetherCare_MVP_Master_Spec.md`
- `docs/AetherCare_MVP_Gap_Review.md`
- `docs/AetherCare_MVP_Implementation_Roadmap.md`

Old docs remain as supporting references:

- `aethercare_codex_system_design.md`
- `aethercare_frontend_backend_api_integration.md`
- `AetherCare_PostgreSQL_Schema.sql`

Future edits must update the master spec first, then sync detailed design/API docs.
