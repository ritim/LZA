# AetherCare API

Home Care Copilot MVP 後端服務。

> 系統設計（authoritative）：[`../docs/system_design/aethercare_codex_system_design.md`](../docs/system_design/aethercare_codex_system_design.md)

## 技術棧

- Java 21 (LTS, Homebrew openjdk@21)
- Spring Boot 3.5.14
- Gradle (Kotlin DSL)
- PostgreSQL 16 + Liquibase
- Redis 7
- Kafka 3.7 (KRaft)
- Lombok
- Testcontainers

## Modular Monolith 結構

```
com.lza.aethercare
├── event           事件接收（event-ingestion）
├── decision        Risk classifier（rule-based）
├── workflow        Workflow engine 核心
├── task            照護任務 + timeout scanner
├── notification    多通道通知（MVP stub）
├── action          使用者回應 callback
├── audit           責任鏈 audit log
├── userprofile     長者與聯絡人
└── common          shared config / error / time
```

## 本機開發

啟動依賴（在 repo root）：

```bash
docker compose up -d
```

預設 host port：
- PostgreSQL `localhost:15432`（避開本機 brew postgresql@18）
- Redis `localhost:16379`（避開本機 brew redis）
- Kafka `localhost:9092`

執行 application：

```bash
./gradlew bootRun
```

預設 profile = `local`，連線到 docker-compose 的 PostgreSQL / Redis / Kafka。

驗證：

```bash
curl http://localhost:8080/api/v1/ping
curl http://localhost:8080/actuator/health
```

## Demo 流程（對應系統設計第 18.1 節）

```bash
# 1. 建立 FALL_DETECTED 事件
curl -X POST http://localhost:8080/api/v1/care-events \
  -H 'Content-Type: application/json' \
  -d '{
    "elderId": 1001,
    "source": "MOBILE_APP",
    "eventType": "FALL_DETECTED",
    "occurredAt": "2026-04-27T12:00:00+08:00",
    "metadata": {"confidence": 0.92, "location": "living_room"}
  }'

# 假設回應 workflowId = 1, level1 taskId = 1

# 2. 查 workflow（應為 WAITING_RESPONSE, currentLevel=1）
curl http://localhost:8080/api/v1/workflows/1

# 3. 等 30 秒（level1 SLA），系統會自動建立 level2 task

# 4. level2 確認安全
curl -X POST http://localhost:8080/api/v1/care-tasks/2/actions \
  -H 'Content-Type: application/json' \
  -d '{"actorId": 2002, "actionType": "CONFIRM_SAFE", "note": "已電話確認，長者安全"}'

# 5. 查 audit timeline（責任鏈）
curl http://localhost:8080/api/v1/workflows/1/audit-logs
```

## 認證流程

- `POST /api/v1/auth/login` → 拿 `accessToken`（1h, JWT HS256）+ `refreshToken`（30d, opaque Base64URL，DB 存 SHA-256 hash）
- access 過期 → `POST /api/v1/auth/refresh {refreshToken}` 換新組（每次強制 rotation：簽新 + revoke 舊 + 串 `replaced_by_id`）
- `POST /api/v1/auth/logout {refreshToken}` 撤銷單一 session（需帶 access bearer header）
- 若 refresh token 被重複使用（reuse detection）→ 整 user 所有 active session 立即撤銷，回 401 `refresh token reuse detected, all sessions revoked`
- 過期或 revoked > 7 天的 token 由 `RefreshTokenCleanupScheduler` 每日 03:30 UTC 批次刪除

## 測試

```bash
./gradlew test                     # unit + integration（Testcontainers）
./gradlew clean build              # 完整 build
```

## Profile

| Profile | 用途 |
|---|---|
| `local`（預設） | 本機開發，連 docker-compose |
| `test`          | Testcontainers 整合測試 |
| `vault`         | 從 HashiCorp Vault 讀 secret（疊在 local 上） |
| `tls`           | 啟用 TLS 連線（PG/Redis/Kafka + HTTPS）|
| `cdc`           | 由 Debezium CDC 接管 outbox publish（關閉 polling） |
| 環境變數覆蓋    | `AETHERCARE_DB_URL`、`AETHERCARE_REDIS_HOST`、`AETHERCARE_KAFKA_BOOTSTRAP` |

## Production secrets / TLS

詳見 [`../docs/deployment/tls-secrets-runbook.md`](../docs/deployment/tls-secrets-runbook.md)。

## Debezium CDC（替代 polling）

詳見 [`../docs/deployment/cdc-debezium-runbook.md`](../docs/deployment/cdc-debezium-runbook.md)。

快速試 CDC：

```bash
docker compose up -d
./scripts/register-debezium-connector.sh
SPRING_PROFILES_ACTIVE=local,cdc ./gradlew bootRun
```

快速啟動 Vault dev mode：

```bash
docker compose up -d vault
./scripts/seed-vault-secrets.sh
SPRING_PROFILES_ACTIVE=local,vault ./gradlew bootRun
```

快速試 TLS（mkcert + docker-compose override）：

```bash
./scripts/gen-dev-certs.sh
docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d
SPRING_PROFILES_ACTIVE=local,tls ./gradlew bootRun
```
