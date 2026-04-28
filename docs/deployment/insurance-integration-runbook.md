# Insurance Integration Runbook

> Status: Phase 2.x 草案。對應後端：
> `aethercare-api/src/main/java/com/lza/aethercare/insurance/`

## 1. 概念：為什麼保險公司需要照護證據

老人居家照護保險（long-term care insurance / LTCI）的承保與理賠都依賴
「事件 → 響應」鏈：

- **核保階段**：保險公司想評估某位長者一年內 SOS / FALL / NO_ACTIVITY 發生
  頻率，以及家屬 / 機構平均回應時間（SLA）。SLA 越好 → 出險時及時處置 →
  重大傷害機率越低 → 保費越低。
- **理賠階段**：發生事故時，保險公司要看「事件當下系統有沒有正確啟動 workflow、
  有沒有人接手、責任鏈是否完整」，以判斷是否屬於系統未盡告知義務 / 是否符合
  理賠條件。

AetherCare 提供「Insurance Evidence」端點，把這條鏈以結構化資料回傳，省去
保險公司自己 join 五六張表。

## 2. API 規格

### Endpoint

```
GET /api/v1/insurance/evidence/{elderId}?from=ISO8601&to=ISO8601
```

| 參數 | 型別 | 必填 | 說明 |
|---|---|---|---|
| `elderId` | path Long | 是 | 長者 id |
| `from` | query ISO-8601 | 否（預設 now-30d） | 區間起始 |
| `to`   | query ISO-8601 | 否（預設 now）     | 區間結束（exclusive）|

### 認證

`Authorization: Bearer <jwt>`，token 必須含 `INSURANCE` role（見 §3 演進路徑）。

### 範例 request

```bash
curl -H "Authorization: Bearer $INSURER_TOKEN" \
  "http://localhost:8080/api/v1/insurance/evidence/1001?from=2026-04-01T00:00:00Z&to=2026-05-01T00:00:00Z"
```

### 範例 response

```json
{
  "elderId": 1001,
  "from": "2026-04-01T00:00:00Z",
  "to":   "2026-05-01T00:00:00Z",
  "totalEvents": 12,
  "totalWorkflows": 12,
  "events": [
    {
      "eventId": 9001,
      "eventType": "FALL_DETECTED",
      "riskLevel": "HIGH",
      "occurredAt": "2026-04-15T08:32:11Z"
    }
  ],
  "workflows": [
    {
      "workflowId": 7301,
      "status": "RESOLVED",
      "currentLevel": 2,
      "startedAt": "2026-04-15T08:32:12Z",
      "completedAt": "2026-04-15T08:33:45Z",
      "taskCount": 2,
      "actionCount": 1,
      "auditChain": [
        "EVENT_CREATED",
        "WORKFLOW_STARTED",
        "TASK_CREATED",
        "NOTIFICATION_SENT",
        "TASK_TIMEOUT",
        "TASK_ESCALATED",
        "TASK_CREATED",
        "NOTIFICATION_SENT",
        "TASK_ACKNOWLEDGED",
        "WORKFLOW_RESOLVED"
      ]
    }
  ]
}
```

## 3. 認證演進路徑

| 階段 | 機制 | 實作 |
|---|---|---|
| MVP（本 commit） | JWT (`INSURANCE` role) | 由 `AuthService.login` 簽發，role 內含 `INSURANCE` |
| Phase 2.5 | API key（per-insurer 長期憑證） | 加 `insurance_api_key` 表 + `ApiKeyAuthFilter`，sha256 比對；可走 IP allowlist |
| Phase 3 | mTLS | k8s ingress 終端 mTLS，client cert subject 對應 insurer 身分；JWT 變成 client cert + signed JWT 雙因素 |

升級不需改 endpoint shape，只換 filter 與 issuer。

## 4. SLA 證明用法（保費評定）

保險公司可同時呼叫：

- `GET /api/v1/insurance/evidence/{elderId}?from=...&to=...` 取得個案證據
- `GET /api/v1/sla/summary?from=...&to=...`（需另一支 USER token，或未來合併授權）
  取得平台整體 SLA

組合報告示例（保險公司內部演算法）：

```
elder 1001 一年內：
  - 12 次事件，全數 RESOLVED
  - 平均升級層級 1.4（多數 level 1 即解決）
  - 平均回應時間 35s（平台整體 42s，優於平均）
→ 評定 LTCI 風險係數 = 0.85 → 月保費降 15%
```

## 5. 隱私權衡

| 欄位 | 給 | 不給 | 原因 |
|---|---|---|---|
| eventType | ✅ | | 已是分類碼，無 PII |
| riskLevel | ✅ | | 同上 |
| occurredAt | ✅ | | 時間戳必要 |
| audit chain action 名稱 | ✅ | | 動作分類，無 PII |
| audit `message` 自由文字 | | ❌ | 可能含家屬姓名 / 地址，runbook 預設不外露 |
| sensor metadata（confidence / location 等） | | ❌ | raw sensor 資料屬於設備層 PII，需另簽資料處理協議才能共享 |
| actorId / 家屬姓名 | | ❌ | 個資保護法（PIPA）限制 |
| GPS 座標 | | ❌ | location precision 即可，座標是高敏資料 |

未來若要加欄位，需走 DPIA（資料保護衝擊評估）流程。

## 6. Rate limit 建議

保險公司通常以 polling 模式抓資料，建議：

| 維度 | 建議值 |
|---|---|
| Per insurer (token) | 100 req/min |
| Per elderId (per insurer) | 10 req/min |
| Window size | `to - from` 不可超過 1 年（避免單次掃全表） |
| Pagination | 若單次回傳 events > 1000 → 改 cursor pagination（Phase 2.5） |

實作可用 Bucket4j + Redis（與既有 `WorkflowLockService` 同一個 Redis）。

## 7. Audit 紀錄

每次查詢都寫一筆 `care_audit_log`：

| 欄位 | 值 |
|---|---|
| `workflow_id` | NULL（系統級查詢，0013 migration 已支援 nullable）|
| `event_id` | NULL |
| `actor_id` | 來自 JWT 的 user id（insurer 帳號） |
| `action` | `INSURANCE_QUERY` |
| `message` | `evidence query elderId=X from=... to=... events=N workflows=M` |

可由 SQL 查詢任一保險公司的歷史請求：

```sql
SELECT actor_id, message, created_at
  FROM care_audit_log
 WHERE action = 'INSURANCE_QUERY'
   AND created_at >= NOW() - INTERVAL '30 days'
 ORDER BY created_at DESC;
```

## 8. 整合範例

### curl

```bash
# 1. 取得 insurer JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"insurer01","password":"insurer123"}' \
  | jq -r .accessToken)

# 2. 查 elder 1001 上個月證據
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/insurance/evidence/1001?from=2026-03-01T00:00:00Z&to=2026-04-01T00:00:00Z" \
  | jq .
```

### Postman collection

```json
{
  "info": { "name": "AetherCare Insurance API" },
  "item": [
    {
      "name": "Login",
      "request": {
        "method": "POST",
        "url": "{{baseUrl}}/api/v1/auth/login",
        "body": {
          "mode": "raw",
          "raw": "{\"username\":\"insurer01\",\"password\":\"insurer123\"}"
        }
      }
    },
    {
      "name": "Get Evidence",
      "request": {
        "method": "GET",
        "header": [
          { "key": "Authorization", "value": "Bearer {{token}}" }
        ],
        "url": "{{baseUrl}}/api/v1/insurance/evidence/1001?from=2026-03-01T00:00:00Z&to=2026-04-01T00:00:00Z"
      }
    }
  ]
}
```

## 9. 對接 SLA：雙資料源組合報告

| 用途 | 端點 | 角色 |
|---|---|---|
| 個案證據（理賠） | `/api/v1/insurance/evidence/{elderId}` | INSURANCE |
| 平台 SLA 統計（核保 / 平台等級評定） | `/api/v1/sla/summary` | USER |

> Phase 2.5 規劃：把 `/api/v1/sla/summary` 開放給 INSURANCE role（read-only），
> 並加 `?aggregateBy=elder|all` 參數，讓保險公司可同時拿到個案 + 平台 baseline，
> 不需要兩個帳號切換。
