-- AetherCare MVP canonical PostgreSQL schema.
-- Source for Claude Code Flyway migration: backend/src/main/resources/db/migration/V1__init_schema.sql
-- Canonical naming: use care_events, not anomaly_events; use care_recipients, not elders.

create table users (
    id bigserial primary key,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(100) not null,
    phone varchar(30),
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table households (
    id bigserial primary key,
    household_name varchar(120) not null,
    timezone varchar(50) not null default 'Asia/Taipei',
    status varchar(20) not null default 'ACTIVE',
    created_by bigint not null references users(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table household_members (
    id bigserial primary key,
    household_id bigint not null references households(id),
    user_id bigint not null references users(id),
    role varchar(30) not null,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (household_id, user_id)
);

create table care_recipients (
    id bigserial primary key,
    household_id bigint not null references households(id),
    full_name varchar(100) not null,
    birth_date date,
    gender varchar(20),
    expression_mode varchar(30) not null default 'ACTIVE',
    baseline_profile_json jsonb,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table recipient_observation_settings (
    id bigserial primary key,
    care_recipient_id bigint not null references care_recipients(id) unique,
    expected_checkin_time time,
    checkin_grace_minutes integer not null default 60,
    max_inactive_minutes_daytime integer not null default 180,
    max_inactive_minutes_night integer not null default 480,
    passive_monitoring_enabled boolean not null default true,
    escalation_policy_json jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table caregiver_links (
    id bigserial primary key,
    care_recipient_id bigint not null references care_recipients(id),
    user_id bigint not null references users(id),
    relationship varchar(50),
    is_primary boolean not null default false,
    notify_priority integer not null default 100,
    created_at timestamptz not null default now(),
    unique (care_recipient_id, user_id)
);

create table activity_logs (
    id bigserial primary key,
    care_recipient_id bigint not null references care_recipients(id),
    activity_type varchar(50) not null,
    source_type varchar(30) not null,
    activity_value varchar(100),
    activity_payload_json jsonb,
    occurred_at timestamptz not null,
    received_at timestamptz not null default now(),
    created_at timestamptz not null default now()
);

create index idx_activity_logs_recipient_occurred_at
    on activity_logs (care_recipient_id, occurred_at desc);

create index idx_activity_logs_type_occurred_at
    on activity_logs (activity_type, occurred_at desc);

create table care_events (
    id bigserial primary key,
    care_recipient_id bigint not null references care_recipients(id),
    event_type varchar(50) not null,
    risk_level varchar(20) not null,
    status varchar(20) not null default 'OPEN',
    detected_at timestamptz not null,
    rule_version varchar(30) not null,
    summary varchar(255) not null,
    detail_json jsonb,
    ai_advice_status varchar(20) not null default 'PENDING',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_care_events_recipient_detected_at
    on care_events (care_recipient_id, detected_at desc);

create index idx_care_events_status_risk
    on care_events (status, risk_level, detected_at desc);

create table care_workflow_instances (
    id bigserial primary key,
    care_event_id bigint not null references care_events(id),
    care_recipient_id bigint not null references care_recipients(id),
    workflow_type varchar(50) not null,
    status varchar(30) not null default 'WAITING_RESPONSE',
    risk_level varchar(20) not null,
    current_level integer not null default 1,
    started_at timestamptz not null default now(),
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_care_workflows_status
    on care_workflow_instances (status, updated_at desc);

create table care_tasks (
    id bigserial primary key,
    workflow_id bigint not null references care_workflow_instances(id),
    care_event_id bigint not null references care_events(id),
    assignee_user_id bigint not null references users(id),
    assignee_type varchar(30) not null default 'FAMILY',
    level integer not null,
    status varchar(30) not null default 'PENDING',
    deadline_at timestamptz not null,
    acknowledged_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_care_tasks_deadline_pending
    on care_tasks (deadline_at)
    where status = 'PENDING';

create table care_actions (
    id bigserial primary key,
    workflow_id bigint not null references care_workflow_instances(id),
    task_id bigint not null references care_tasks(id),
    actor_user_id bigint not null references users(id),
    action_type varchar(50) not null,
    note text,
    created_at timestamptz not null default now()
);

create table care_audit_logs (
    id bigserial primary key,
    workflow_id bigint references care_workflow_instances(id),
    care_event_id bigint references care_events(id),
    actor_user_id bigint references users(id),
    action varchar(100) not null,
    message text not null,
    metadata_json jsonb,
    created_at timestamptz not null default now()
);

create index idx_care_audit_logs_workflow_created_at
    on care_audit_logs (workflow_id, created_at);

create table notification_records (
    id bigserial primary key,
    care_event_id bigint not null references care_events(id),
    target_user_id bigint not null references users(id),
    channel varchar(20) not null,
    delivery_status varchar(20) not null default 'PENDING',
    provider_message_id varchar(120),
    sent_at timestamptz,
    failure_reason varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table ai_advice_records (
    id bigserial primary key,
    care_event_id bigint not null references care_events(id) unique,
    prompt_version varchar(30) not null,
    model_name varchar(100) not null,
    advice_text text not null,
    structured_json jsonb,
    status varchar(20) not null default 'SUCCESS',
    created_at timestamptz not null default now()
);

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

create index idx_ai_chat_messages_workflow_created_at
    on ai_chat_messages (workflow_id, created_at);
