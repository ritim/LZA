# Multi-tenant SaaS Roadmap

> 系統設計來源：[`../system_design/aethercare_codex_system_design.md`](../system_design/aethercare_codex_system_design.md) §20 Phase 2

## 1. 為什麼 multi-tenant

AetherCare 目標是 SaaS 化，讓不同保險公司、長照機構、社福團體共用同一套程式碼，但資料完全隔離。設計重點：

- **單一 codebase + 單一 deployment** 攤平 ops 成本
- **Row-level 隔離** 確保不同 tenant 的長者 / 事件 / 任務不互通
- **Tenant-level 計費 / 配額** 給商業模式（按長者數、API call、通知量）
- **GDPR / 合約終止** 時可批次匯出 / 刪除某 tenant 全部資料

## 2. 模式選擇 trade-off matrix

| 模式 | 隔離強度 | DDL 維運 | 成本 | 跨 tenant 統計 | 適用 |
|---|---|---|---|---|---|
| Shared schema + tenant_id | 中（靠 app filter） | 一份 schema | 最低 | 最容易 | MVP / 小客戶 |
| Schema per tenant | 高（PG schema 隔離） | 每 tenant migrate | 中 | 較難 | 大客戶 / 中度合規 |
| DB per tenant | 最高 | 每 tenant 一份 DB | 最高 | 最難 | 政府 / 醫療法規 |

**MVP 選擇：shared schema + tenant_id column + Hibernate `@Filter`**。理由：

- 1 條 SQL 就能加 tenant；不影響既有 query 寫法
- Hibernate session-level filter 自動注入 `WHERE tenant_id=?`，application code 不需手動帶
- 升級到 schema-per-tenant 時，row-level 資料可直接 partition by tenant_id 拆出

## 3. 已實作（本 commit）

- `tenant` table（`0014-create-tenant.yaml`）：id / code / display_name / enabled，seed 兩筆 (`default` id=1, `premium` id=2)
- 4 核心 entity 加 `tenant_id BIGINT NOT NULL DEFAULT 1` + FK + index：
  - `app_user`（`AppUser` entity 加 `Long tenantId`，builder default `1L`）
  - `care_event`（`@Filter`）
  - `care_workflow_instance`（`@Filter`）
  - `care_task`（`@Filter`）
- `com.lza.aethercare.tenant`：
  - `entity/Tenant.java` + `repository/TenantRepository.java`
  - `context/TenantContext.java`（ThreadLocal，`getOrDefault()` fallback to 1L）
  - `aspect/TenantFilterAspect.java`（AOP 自動 enable Hibernate filter，僅 `event.service` / `workflow.service` / `task.service`）
  - `package-info.java` 統一定義 `@FilterDef tenantFilter`
- JWT claim 新增 `tenantId`：
  - `JwtService.generate` / `parse` 加 claim
  - `DecodedJwt` 多一個 `tenantId` 欄位
  - `AppUserDetails` 多一個 `tenantId` 欄位 + 兩個 `fromToken` overload（含 backward compat）
- `JwtAuthenticationFilter`：parse 後 `TenantContext.set(decoded.tenantId())`，`finally` 中 `clear()`
- `LoginResponse` 加 `Long tenantId`（給前端用）
- `DemoUserSeeder`：upsert 加 tenantId 參數，新增 `premium-family01 / premium-family123` (tenantId=2)
- `application.yml` append `aethercare.tenant.{default-id, default-code}`
- IT 新增 `should_isolate_workflows_across_tenants`（FallDetectedEndToEndIT）

## 4. 剩餘 entity 演進（roadmap）

priority 由高至低：

| Entity | Priority | 原因 |
|---|---|---|
| `care_action` | High | 跟 task / workflow 一樣是 user-facing 主軸 |
| `care_audit_log` | High | 合規審計每 tenant 必須隔離 |
| `elder_activity_event` / `elder_activity_baseline` | High | 長者個人資料隱私 |
| `outbox_message` | Medium | 訊息流與 tenant 計費掛勾 |
| `refresh_token` | Medium | 跟 user 1:1，間接隔離；明確標記較好 audit |
| `app_user_role` | Low | 透過 user 隔離；隔不隔差別不大 |
| `care_contact_escalation` | Low | demo seed 表，待 onboarding flow 設計時一起做 |

每個 entity 演進步驟：

1. Liquibase changeset 加 `tenant_id BIGINT NOT NULL DEFAULT 1` + FK + index
2. JPA entity 加 `Long tenantId` 欄位 + `@Filter`
3. 對應 service 寫入時帶 `TenantContext.getOrDefault()`，或從關聯 entity 帶
4. 新增 IT 確認跨 tenant 隔離

## 5. 升級路徑

```
Phase 2.x  shared schema + tenant_id          ← MVP
Phase 3    schema per tenant（PG schema）     ← 大客戶（>10K 長者）
Phase 4    DB per tenant                      ← 政府 / 醫療強合規
```

升級觸發條件：

- **Phase 2 → 3**：單一 tenant > 10K 長者、或 query latency p99 > 500ms（schema 切割可降索引壓力）
- **Phase 3 → 4**：客戶簽約強制要求物理隔離 / 在地端部署

## 6. Tenant onboarding flow

```
admin POST /api/v1/admin/tenants {code, displayName}
  → INSERT tenant
  → 建立預設 admin user (tenantId=新tenant.id)
  → 預設 escalation contact（template）
  → 寄出 admin password reset link
```

實作時機：Phase 2.x 後半，當第一個 paying 客戶簽約時。

## 7. SaaS billing

| 計費維度 | 抓取點 | 工具 |
|---|---|---|
| 長者數 | `elder` 表 + tenant_id | nightly cron 計算 |
| 通知量 | `notification.sent` Kafka topic + tenant_id | Prometheus counter |
| API call rate | request log + JWT.tenantId | Grafana / Cloud LB |
| 儲存量 | `care_audit_log` row count + outbox size | Postgres pg_stat |

**Stripe 整合方向**：每 tenant 對應 Stripe customer + subscription；usage-based pricing 用 metered billing API 每月 push usage record。

## 8. 跨 tenant data export（GDPR / 合約終止）

```
admin POST /api/v1/admin/tenants/{id}/export
  → 鎖 tenant.enabled=false
  → 開 transaction：select * from <each table> where tenant_id=?
  → 序列化成 zip（PG dump 或 JSONL）→ S3 presigned URL
  → 通知 admin email
```

合約終止後 X 天 hard delete（`DELETE FROM ... WHERE tenant_id=?` 或 partition drop）。

## 9. Tenant-level config override

每 tenant 可覆蓋的設定：

- SLA seconds（`care_contact_escalation` 已支援，但要加 tenant_id）
- Notification channels enabled（SMS / LINE / WeChat）
- Risk threshold（不同客戶可能對 LOW vs HIGH 認知不同）
- Anomaly z-score threshold（預設 3.0）

實作：`tenant_config` 表 (tenant_id, key, value JSONB)；service 讀設定先查此表 fallback 到 application.yml。

## 10. 監控

每 tenant 加 Prometheus label：

```
care_event_created_total{tenant="default"} 1234
care_event_created_total{tenant="premium"} 56
notification_sent_total{tenant="default", channel="LINE"} 890
```

Grafana dashboard 可 filter by tenant，每 tenant 一張卡片：事件數 / 升級率 / 解決率 / 通知失敗率。

Alert rules 加 `by (tenant)`：

```promql
sum(rate(workflow_unresolved_total[1h])) by (tenant) > 0.1
```

## Demo

```bash
# 1. default tenant family01 建事件
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"family01","password":"family123"}' | jq -r .accessToken)
curl -X POST localhost:8080/api/v1/care-events \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"elderId":1001,"source":"MOBILE_APP","eventType":"FALL_DETECTED","occurredAt":"2026-04-27T15:00:00+08:00"}'
# 假設 workflowId=1

# 2. premium tenant premium-family01 試 GET → 應 404
PTOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"premium-family01","password":"premium-family123"}' | jq -r .accessToken)
curl -i localhost:8080/api/v1/workflows/1 -H "Authorization: Bearer $PTOKEN"
# 預期 HTTP/1.1 404 — Hibernate filter 把 default tenant 的 workflow 隱藏掉
```
