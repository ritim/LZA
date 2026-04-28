# AetherCare AI MVP — Autopilot Spec

## 來源
- 系統設計文件（authoritative）：`docs/system_design/aethercare_codex_system_design.md`
- 使用者指令：建立完整 MVP 後端，技術選型已固定，使用者已離開，全自動執行直到驗收標準全綠。

## 技術選型（固定，不得變更）

| 項目 | 選定 |
|---|---|
| Language | Java 21 (LTS)（原規劃 25，因 Gradle 8.14 Kotlin DSL 不相容 Java 25 而降版） |
| Framework | Spring Boot 3.5.14 |
| Build | Gradle (Kotlin DSL) |
| Group / Package | `com.lza` / `com.lza.aethercare` |
| Artifact | `aethercare-api` |
| DB | PostgreSQL 16 + Liquibase |
| Cache | Redis 7 |
| Bus | Kafka 3.7 (KRaft) |
| 結構 | Modular monolith，domain-first packages |
| 整合測試 | Testcontainers |

## 專案路徑
- Repo root：`/Users/yao/0.Projects/LZA`
- Spring Boot 子目錄：`/Users/yao/0.Projects/LZA/aethercare-api`

## 已完成基底
- Spring Initializr Gradle KTS 骨架（含 web / data-jpa / validation / actuator / kafka / data-redis / liquibase / postgresql / lombok）
- `application.yml` + `application-local.yml`
- `db/changelog/db.changelog-master.yaml`（includeAll changes/）
- `AethercareApiApplication.java`

## 待實作範圍（A–L 模組）

A. 基礎設施：`docker-compose.yml`（PG/Redis/Kafka KRaft + healthcheck + volumes）、根 `.gitignore`、兩個 `README.md`、9 個 domain `package-info.java`。

B. Liquibase migrations（PostgreSQL）：6 張表 — `care_event`, `care_workflow_instance`, `care_task`, `care_action`, `care_audit_log`, `care_contact_escalation`，含 BIGSERIAL/TIMESTAMPTZ/index/FK。

C. JPA Entity + Repository：每張表，含 `@Version` 樂觀鎖、conditional update。

D. Workflow Engine：CareEvent → WorkflowInstance → Level 1 CareTask → notify stub → audit。狀態 enum 集中、`@Transactional`、conditional update 防重複 timeout/action（文件 13.1, 13.2）。

E. Decision Service：rule-based event_type → risk_level（in-memory map）。

F. Timeout Scanner：`@Scheduled fixedDelay = 5000`，conditional update 鎖 PENDING + 過期 task；無下一層 → workflow UNRESOLVED。

G. REST API：
- POST `/api/v1/care-events`
- POST `/api/v1/care-tasks/{taskId}/actions`（CONFIRM_SAFE / NEED_HELP）
- GET `/api/v1/workflows/{workflowId}`
- GET `/api/v1/workflows/{workflowId}/audit-logs`
- `@Valid` DTO + 全域 `@ControllerAdvice` 例外處理

H. Notification stub：Channel enum `LINE / SMS / EMAIL / PUSH`，僅 log，寫入 audit。

I. Audit Service：集中入口記錄文件第 15 節列舉所有 action 類型。

J. Kafka：MVP topics `care.event.created / care.workflow.started / care.task.created / care.notification.sent / care.action.received / care.audit.created` — Producer + sample consumer log。

K. Redis：`workflow:lock:{id}`（SETNX）、`elder:latest-status:{id}`。

L. 測試：每個 service 至少一個 unit test（Mockito）；一個 Testcontainers 整合測試跑 FALL_DETECTED 完整 demo（事件 → workflow → level1 → timeout → escalation level2 → CONFIRM_SAFE → RESOLVED → audit timeline）。

## 驗收標準
1. `docker compose up -d` 三個服務 healthy
2. `./gradlew clean build` 全綠
3. `./gradlew test` 含 Testcontainers 整合測試全綠
4. `./gradlew bootRun` 可啟動，`/actuator/health` UP
5. curl 跑通文件第 18.1 節功能驗收

## 非範圍
- 真正 AI 模型 / Camera Vision / 醫療診斷 / 119 撥打 / 保險 API / 多租戶 SaaS / 排班（文件第 19 節）
- 真實 LINE/SMS/Email 推送（用 stub）
- Spring Modulith / Temporal / Durable workflow（第二階段）

## 語言
- User-facing 文字 / 註解 / commit message：zh-TW
- 程式碼識別字：英文
