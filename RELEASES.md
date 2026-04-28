# AetherCare AI — Release Notes

> 柳智有限公司 / AetherCare AI — Home Care Copilot MVP

---

## v1.0-rc1 (2026-04-28)

**MVP + Phase 2 framework 完整實作 + 5 hard blocker 修復；可 ship 為 demo / closed alpha**

### Tagline
> 不只偵測風險，而是確保風險被處理 — Home Care Copilot / Care Execution Platform

### 核心閉環（系統設計文件 §1-22 全覆蓋）

```
事件 → Decision (rule-based) → Workflow → Notification → SLA Timeout
     → Escalation → Action callback → Audit timeline → 責任鏈完整可查
```

### 範圍

| 模組 | 內容 |
|---|---|
| **後端** (`aethercare-api/`) | Spring Boot 3.5.14 / Java 21 / Gradle KTS / 60 tests / 1 skipped |
| **前端** (`aethercare-web/`) | Vue 3 + TS + Pinia + Element Plus / 4 views |
| **基礎設施** | docker-compose: PG 16 / Redis 7 / Kafka 3.7 KRaft / Kafka Connect (Debezium 2.7) / Vault 1.18 |
| **Schema** | Liquibase 16 changesets（含 baseline tag + 顯式 rollback） |
| **Docs** | 6 篇 production runbook（CDC / TLS-secrets / Liquibase-rollback / Insurance / Multi-tenant / Temporal） |

### 統計

- **Java 類別**：~150 跨 12 個 module（event / workflow / task / decision / notification / action / audit / userprofile / common / anomaly / tenant / insurance / auth）
- **Liquibase changesets**：16（0001 → 0016）
- **Tests**：60 unit + IT / 1 skipped / 0 failed
- **API endpoints**：14
- **Profiles**：local / test / vault / cdc / tls
- **Demo accounts**：admin / family01 / family02 / insurer01 / premium-family01

---

## Highlights — 20 Commits

### Foundation (1 commit)
- `f9ead33` MVP 後端骨架：Spring Boot + state machine + conditional update + 6 entity + Workflow Engine + Testcontainers IT

### Phase 4 Reviewer Fixes (2 commits)
- `6970e3b` Critical/Major 修復：escalate 雙 advanceLevel 合併、Redis lock token-based、SystemClock UTC、dead code 清理、KafkaPublishingListener fallbackExecution、metadata 8KB 限制
- `adc85fc` docker-compose host port 15432/16379 避 brew PG/Redis 衝突

### Security Hardening (5 commits)
- `d9f93fb` Spring Security JWT (HS256, jti, 1h)，actorId 從 SecurityContext
- `226f5fc` PII log masking (elderId / actorId / username)
- `9a4b752` Refresh token + rotation + reuse detection (REQUIRES_NEW handler)
- `edc268d` Actuator 拆獨立 management port 9001 + Basic auth + ACTUATOR role
- `82b4a5d` AuthServiceTest + JwtServiceTest unit test

### Reliability (3 commits)
- `7a9e7aa` Kafka Transactional Outbox + exponential backoff + DEAD_LETTER + Counter
- `d8d6bfc` Debezium CDC 替代 OutboxPublisher polling（postgres logical WAL + EventRouter SMT）
- `1b29fc6` OutboxCleanupScheduler 定期清 PUBLISHED 訊息

### Infrastructure (1 commit)
- `e388c55` HashiCorp Vault secrets management + TLS profile 範例 + mkcert dev cert + production runbook

### Database (1 commit)
- `89dba7d` Liquibase rollback 顯式 section + 0011 mvp-baseline tag + docker-based CLI + runbook

### Frontend (1 commit)
- `2ce6348` Vue 3 dashboard demo (login / dashboard / workflow detail with auto-polling + deadline 倒數)

### Phase 2 — §20 全 6 項 (4 commits)
- `ccac070` AI Anomaly Detection (rule + z-score baseline + scheduler)
- `606e7ec` Multi-signal Fusion + Temporal roadmap + SLA Dashboard (後端 + Vue view) + Insurance API + INSURANCE role + audit
- `cf7ecb3` IT 加 NEED_HELP + ACKNOWLEDGE 第二三條場景
- `8411050` Multi-tenant SaaS framework (tenant table + JWT claim + TenantContext + AOP filter aspect)

### Final Audit + Hard Blocker Fix (1 commit)
- `cddca86` Phase 4 final reviewer 5 項 production blockers：Multi-tenant 補 4 entity / JWT fail-fast / seed-demo-users default false / dev secret prod fail-fast / scanner @ConditionalOnProperty

### Cleanup
- `01d8c68` 排除 aethercare-web/.omc/ OMC state

---

## Production Readiness

### ✅ Ready for Demo / Closed Alpha
- 60 tests pass (含端到端 IT：CONFIRM_SAFE / NEED_HELP / ACKNOWLEDGE / refresh rotation / reuse detection / multi-tenant 隔離 / 401 / 重複 409)
- Phase 4 final 三 reviewer 共識 SHIP_AS_DEMO / APPROVE_WITH_NITS
- 5 個 production hard blockers 已修

### ⚠️ Production 上線前要驗證 / 完成
詳見 `docs/deployment/*.md` 6 篇 runbook：
- 必設 env：`AETHERCARE_JWT_SECRET`, `AETHERCARE_ACTUATOR_PASSWORD`, `AETHERCARE_DB_PASSWORD`, `AETHERCARE_VAULT_TOKEN` (Vault profile)
- 啟用：`SPRING_PROFILES_ACTIVE=prod` 觸發 fail-fast 檢查
- TLS：`./scripts/gen-dev-certs.sh` + `docker-compose.tls.yml` (production 用真實 CA)
- Secrets：Vault profile + AppRole / k8s auth (見 tls-secrets-runbook.md §3)
- Debezium CDC：取代 polling 達成低延遲 (見 cdc-debezium-runbook.md)
- Liquibase rollback：`./scripts/liquibase-rollback.sh tag <release-tag>` (見 liquibase-rollback-runbook.md)

### Known Tech Debt → v1.1 milestone
- Rate limit (`/auth/login`, `/elders/*/activities`, `/insurance/evidence/*`) — Bucket4j + Redis
- INSURANCE_QUERY audit 寫 elderId 改 mask
- Frontend localStorage JWT → httpOnly cookie + CSRF token
- WorkflowEngine interface 加 ScheduledImpl adapter（避免 dead interface）
- BaselineCalculator.recalculateAll 加 @Scheduled
- Multi-tenant scheduler 加 ShedLock（multi-pod leader election）
- Insurance evidence GDPR 細節：限制 elderId 屬於 insurer 簽約範圍

### Production Deployment Checklist
- [ ] `AETHERCARE_JWT_SECRET` 設成 production secret（Vault / AWS Secrets Manager）
- [ ] `AETHERCARE_ACTUATOR_PASSWORD` 設成 strong password
- [ ] `AETHERCARE_SEED_DEMO_USERS=false`（預設已是）
- [ ] `MANAGEMENT_SERVER_ADDRESS=127.0.0.1`（避免外部存取）
- [ ] `AETHERCARE_CORS_ORIGINS` 設成真實前端 domain
- [ ] `SPRING_PROFILES_ACTIVE` 包含 `prod` 觸發 fail-fast
- [ ] PG / Redis / Kafka 啟用 TLS（profile=tls 或 production override）
- [ ] Vault 改 AppRole / k8s auth；不用 root token
- [ ] Debezium connector 用 Vault inject `database.password`
- [ ] Prometheus scrape `:9001/actuator/prometheus` 啟用 + 告警
- [ ] DB backup + Liquibase tag baseline
- [ ] Load test (Gatling / k6) 驗證 100+ concurrent workflow

---

## Demo Flow

### 啟動
```bash
cd /Users/yao/0.Projects/LZA
docker compose up -d              # PG 15432 / Redis 16379 / Kafka 9092 / Vault 8200 / Connect 8083
cd aethercare-api && ./gradlew bootRun  # API 8080 + Actuator 9001
cd ../aethercare-web && npm install && npm run dev  # Vue 5173
```

### 端到端 Demo（瀏覽器）
1. 開 `http://localhost:5173`
2. 登入 `family01 / family123`
3. 點「建立 FALL_DETECTED」→ 跳到 workflow 詳情頁
4. 看 level 1 task PENDING 倒數 30 秒
5. 30 秒後 timeout scanner 自動建 level 2 task
6. 開另一視窗登入 `family02 / family123`，CONFIRM_SAFE level 2 task
7. workflow status → RESOLVED；audit timeline 看到 10 筆完整責任鏈

### Anomaly Detection Demo
詳見 `aethercare-api/README.md#anomaly-detection-demo`

### Insurance Evidence Demo
登入 `insurer01 / insurer123` → `GET /api/v1/insurance/evidence/1001?from=...&to=...`

### SLA Dashboard
登入後點 dashboard 的「SLA Dashboard」按鈕看 `/sla` 頁

### Multi-tenant Isolation Demo
- `family01`（default tenant）建立的 workflow，`premium-family01`（premium tenant）GET 會回 404
- IT `should_isolate_workflows_across_tenants` 自動驗證

---

## Acknowledgments

- 系統設計 spec：`docs/system_design/aethercare_codex_system_design.md`（22 節 + Phase 2 §20 完整對齊）
- 由 OMC autopilot + executor + 多 reviewer 協作完成
