# Debezium CDC Outbox Runbook

> 本文檔說明如何用 Debezium PostgreSQL Connector + Outbox Event Router SMT 取代 application 內 OutboxPublisher polling，將 `outbox_message` 表的變更直接由 PostgreSQL WAL 推送到 Kafka。

## 1. 何時用 CDC vs Polling

| 情境 | 推薦方案 |
|---|---|
| 流量低（< 1k msg/min）、stack 想保持簡單、運維資源有限 | **Polling**（預設） |
| 流量大、需要 < 100ms 端到端延遲、想去除 polling 對 DB 的固定負載 | **CDC** |
| 早期開發 / Demo / CI | **Polling**（不需多起一個 Connect cluster） |
| Production 多實例、需要嚴格 ordering 與 exactly-once 行為 | **CDC**（Debezium 原生 ordering） |

決策關鍵：
- Polling 每 2s 一輪 SELECT，DB 負載穩定但延遲下界 ~ fixedDelay
- CDC 監聽 WAL，延遲 < 100ms；但多了 Connect cluster 與 replication slot 要顧
- 兩者**嚴禁同時啟用**，會造成同一筆 outbox row 被送兩次

## 2. 本機試 CDC

```bash
# 1. 起所有服務（postgres / kafka / connect / vault / redis）
docker compose up -d

# 2. 等 Connect 就緒、註冊 connector
./scripts/register-debezium-connector.sh

# 3. 啟用 cdc profile，關閉 application 端 polling
SPRING_PROFILES_ACTIVE=local,cdc ./gradlew bootRun

# 4. 觸發事件，觀察 < 100ms 出現在 Kafka
curl -X POST http://localhost:8080/api/v1/care-events \
  -H 'Content-Type: application/json' \
  -d '{"elderId":1001,"source":"MOBILE_APP","eventType":"FALL_DETECTED","occurredAt":"2026-04-27T12:00:00+08:00","metadata":{}}'

# 5. 看 Kafka 上 care.event.created topic
docker exec -it aethercare-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic care.event.created --from-beginning
```

## 3. PostgreSQL 設定

`docker-compose.yml` 中 postgres 已加：

```yaml
command:
  - "postgres"
  - "-c"
  - "wal_level=logical"
  - "-c"
  - "max_wal_senders=4"
  - "-c"
  - "max_replication_slots=4"
```

注意事項：
- `wal_level=logical` 是必要前提，否則 pgoutput plugin 無法 decode
- `max_wal_senders` / `max_replication_slots`：給 Debezium 留 slot；多 connector 時要調大
- 建議 monitor `pg_replication_slots` view 的 `confirmed_flush_lsn` 與 `pg_current_wal_lsn` 差距即為 lag
- **Replication slot 不能丟**：slot 在但 consumer 不消費，WAL 會無限增長吃掉 disk

```sql
-- 觀察 slot 狀態與 lag
SELECT slot_name, active, restart_lsn, confirmed_flush_lsn,
       pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn)) AS lag
FROM pg_replication_slots;

-- 緊急刪除無人用的 slot（會造成 connector 重註冊時從 WAL latest 開始）
SELECT pg_drop_replication_slot('aethercare_outbox_slot');
```

## 4. Connector 操作

```bash
# 查狀態（task running / failed / paused）
curl -s http://localhost:8083/connectors/aethercare-outbox-connector/status | jq

# 查 config
curl -s http://localhost:8083/connectors/aethercare-outbox-connector/config | jq

# 查 task 細節（含 error trace）
curl -s http://localhost:8083/connectors/aethercare-outbox-connector/tasks | jq

# 更新 config（PUT 整份）
curl -X PUT -H 'Content-Type: application/json' \
  http://localhost:8083/connectors/aethercare-outbox-connector/config \
  -d @scripts/debezium-outbox-connector.json

# Restart connector / task
curl -X POST http://localhost:8083/connectors/aethercare-outbox-connector/restart
curl -X POST http://localhost:8083/connectors/aethercare-outbox-connector/tasks/0/restart

# Pause / Resume
curl -X PUT http://localhost:8083/connectors/aethercare-outbox-connector/pause
curl -X PUT http://localhost:8083/connectors/aethercare-outbox-connector/resume

# 刪除 connector（會清 slot）
curl -X DELETE http://localhost:8083/connectors/aethercare-outbox-connector
```

## 5. Production 部署

- **Kubernetes**：用 [Strimzi Kafka Operator](https://strimzi.io/) 部署 Kafka Connect cluster
  - 透過 `KafkaConnect` CRD 宣告 Connect cluster
  - 透過 `KafkaConnector` CRD 管理 connector lifecycle
  - Connector config 透過 ConfigMap 管理，Secret 注入 DB password
- **Tasks 配置**：`tasks.max=1` —— Debezium PostgreSQL connector 一個 connector 對應一個 task（CDC 是有序串流，無法平行化讀單一 slot）
- **Connect cluster size**：建議 ≥ 2 worker，connector 自動 rebalance 到健康 worker
- **Resource**：每個 connector ~ 512Mi heap 起跳，視吞吐調整

## 6. 監控

| 指標 | 來源 | 意義 |
|---|---|---|
| `pg_current_wal_lsn() - pg_replication_slots.confirmed_flush_lsn` | PG | Replication lag (bytes) |
| `source-record-poll-rate` | Connect JMX | 每秒從 source 讀的 record 數 |
| `source-record-write-rate` | Connect JMX | 每秒寫到 Kafka 的 record 數 |
| `connector-task-state` | Connect JMX | RUNNING / FAILED / PAUSED |
| Debezium `MilliSecondsBehindSource` | Debezium JMX | source 到 connector 的延遲 |
| Debezium `LastEvent` | Debezium JMX | 最後處理的 event 時間 |

建議 alert：
- Replication lag > 100MB 持續 5min
- `connector-task-state != RUNNING` 持續 1min
- `source-record-write-rate == 0` 但 outbox 有新 row（需自訂 join alert）

## 7. Failover

- **Connect cluster failover**：N 個 worker 中一個掛掉，connector 與 task 自動 rebalance 到剩餘 worker
- **PostgreSQL failover（physical replica → primary）**：
  - 必須用 logical decoding 並在 standby 啟用 `hot_standby_feedback=on`
  - PG 13+ 支援 logical decoding from standby（但 Debezium 支援度視版本）
  - **建議**：failover 後重建 slot，從 WAL latest 開始（會丟未消費 row，需配合 outbox 補送機制）
- **Connect cluster 整個掛掉**：
  - Outbox row 持續累積在 DB（status 不會被改）
  - 切回 polling profile（`SPRING_PROFILES_ACTIVE=local`）由 OutboxPublisher 接手即可

## 8. Disaster Recovery

| 情境 | 處理 |
|---|---|
| Connect cluster 全掛 | 移除 connector → 留 outbox 累積 → 切回 polling profile 補送 |
| Slot lag 持續增長吃掉 disk | 緊急 `pg_drop_replication_slot` → 重建 connector（會丟未送 row，依靠 outbox 補送） |
| Connector 重複送（splits） | Outbox 設計已 idempotent（`markPublished` SET WHERE status=PENDING）；下游 consumer 也應 idempotent |
| Schema 變更（outbox 加欄位） | 可線上加欄位；EventRouter SMT 不會壞，但要更新 `transforms.outbox.table.fields.additional.placement` |

重建 slot SOP：

```bash
# 1. 停 connector
curl -X DELETE http://localhost:8083/connectors/aethercare-outbox-connector

# 2. 確認 slot 已被清（DELETE connector 會自動清，否則手動）
docker exec -it aethercare-postgres psql -U aethercare -d aethercare \
  -c "SELECT pg_drop_replication_slot('aethercare_outbox_slot');"

# 3. 切回 polling 補送累積的 outbox
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun  # 等 outbox 清空

# 4. 重建 connector
./scripts/register-debezium-connector.sh
```

## 9. 與 Polling 共存（嚴禁！）

⚠️ **嚴禁同時啟用 OutboxPublisher 與 Debezium connector**，會造成同一 outbox row 被送兩次（race condition），下游可能收到重複訊息。

切換 SOP（polling → CDC）：

```bash
# 1. Drain：先讓 polling 把 outbox 清空（觀察 outbox_message WHERE status='PENDING' 為 0）
# 2. 改 profile
export SPRING_PROFILES_ACTIVE=local,cdc
# 3. 重啟 application（OutboxPublisher 因 ConditionalOnProperty 不會被建立）
# 4. 註冊 connector
./scripts/register-debezium-connector.sh
```

切換 SOP（CDC → polling）：

```bash
# 1. 停 connector
curl -X DELETE http://localhost:8083/connectors/aethercare-outbox-connector
# 2. 改 profile
export SPRING_PROFILES_ACTIVE=local
# 3. 重啟 application（OutboxPublisher 啟用，會接手未送 row）
```

## 10. EventRouter SMT 細節

關鍵 config：

| 欄位 | 值 | 說明 |
|---|---|---|
| `route.by.field` | `target_topic` | 用 outbox row 的 `target_topic` 欄位決定 Kafka topic |
| `route.topic.replacement` | `${routedByValue}` | 直接用欄位值當 topic 名（不加 prefix） |
| `table.field.event.id` | `id` | 對映 outbox.id 為 Kafka header `id` |
| `table.field.event.key` | `message_key` | outbox.message_key 為 Kafka message key |
| `table.field.event.payload` | `payload` | outbox.payload 為 Kafka message value |
| `table.fields.additional.placement` | (空) | 不放額外 metadata header |
| `table.expand.json.payload` | `true` | 把 payload 欄位的 JSON string 展開成結構化 message |

為什麼**不**用 Debezium 內建 outbox table schema：
- 我們現有 `outbox_message` schema 已被 polling path 使用，要與 CDC 共用 schema
- Debezium 內建 outbox 預期欄位 `aggregateid` / `aggregatetype` / `type` / `payload`，與我們現有欄位不同
- EventRouter SMT 透過 `table.field.event.*` 設定可對映任意欄位名，相容性足夠

注意：
- `expand.json.payload=true` 要求 payload 欄位是合法 JSON（我們的 outbox payload 已是 JSON string）
- 若 payload 是 plain string，要設 `false`

## 11. 舊 outbox 清理

CDC 模式下：
- `snapshot.mode=never` → Debezium 不會回讀 PUBLISHED 狀態的舊 row
- 但 PUBLISHED row 會留在 DB，長期累積
- **建議加 cleanup scheduler**（DELETE WHERE status='PUBLISHED' AND published_at < now() - INTERVAL '7 days'）
- 或用 PostgreSQL `pg_partman` 做 partition by month，drop 舊 partition

簡易 cleanup（手動或 cron）：

```sql
DELETE FROM outbox_message
WHERE status = 'PUBLISHED'
  AND published_at < now() - INTERVAL '7 days';
```

DEAD_LETTER row 不要自動刪，需人工檢視 root cause。
