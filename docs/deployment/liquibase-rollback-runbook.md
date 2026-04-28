# Liquibase Rollback Runbook

> 何時、如何安全地回退 DB schema。MVP 用 docker-based liquibase CLI；
> production 建議跑在 CI/CD pipeline（GitHub Actions / GitLab CI）或 k8s init container。

---

## 1. Rollback 何時有用

| 情境 | 用法 |
|---|---|
| 剛 deploy 新版本 schema 後出 bug | `rollback count 1` 退回上版 |
| 大版本回退（多 changeset） | `rollback tag <release-tag>` |
| Hotfix 開發分支需要乾淨 schema | dev DB 跑 `rollbackAll` 清掉 |
| 災難復原：DB 部分 corrupt | 先 `dropAll` 再 `update` 重建（極端情況） |

⚠️ **生產環境 rollback 等於 schema 修改 + 資料 mutation**。要先 backup！

---

## 2. 已實作的 rollback 規則

| Changeset | 自動 derive | 顯式 rollback | 備註 |
|---|---|---|---|
| 0001 care_event | ✅ dropTable | – | 連 index 一起 drop |
| 0002 care_workflow_instance | ✅ | – | FK 自動 drop |
| 0003 care_task | ✅ | – | |
| 0004 care_action | ✅ | – | |
| 0005 care_audit_log | ✅ | – | |
| 0006 care_contact_escalation | ✅ | – | UNIQUE 自動 drop |
| **0007 seed-demo-data** | ❌ | ✅ DELETE 兩 row | insert 不能 auto |
| 0008 app_user | ✅ | – | |
| 0009 outbox_message | ✅ | – | |
| 0010 refresh_token | ✅ | – | |
| 0011 tag mvp-baseline | – | empty | tagDatabase 不需 rollback |

---

## 3. 本機 / dev rollback（docker-based）

```bash
# 查當前狀態
./scripts/liquibase-rollback.sh status

# 查歷史（看 applied changesets）
./scripts/liquibase-rollback.sh history

# Rollback 最近 1 個 changeset
./scripts/liquibase-rollback.sh count 1

# Rollback 到 mvp-baseline tag
./scripts/liquibase-rollback.sh tag mvp-baseline

# 對當前 DB 加 release tag（用於未來 rollback 錨點）
./scripts/liquibase-rollback.sh tag-now release-2026-04-28
```

不依賴 gradle plugin，純 docker（`liquibase/liquibase:4.31`）+ host.docker.internal 連 PG。

---

## 4. Tag 策略

每個 release（含 hotfix）都應該打 tag，方便將來 rollback 錨點：

```bash
# Deploy v1.0 後立刻 tag
./scripts/liquibase-rollback.sh tag-now release-v1.0
```

**Naming convention**：
- `release-<semver>`（例：`release-v1.0`、`release-v1.1`）
- `hotfix-<date>-<issue>`（例：`hotfix-2026-04-28-CRIT-1234`）
- 已內建：`mvp-baseline`（Liquibase 0011-tag changeset 自動加）

---

## 5. Production rollback SOP

### 5.1 前置作業（非協商）
1. **DB backup**：`pg_dump aethercare > backup-$(date +%Y%m%d-%H%M).sql`
2. **通知 oncall**：Slack `#deploy` channel 公告 rollback window
3. **暫停寫入**：deploy `read-only` config 或縮 application replica 數
4. **驗證 dependencies**：confirm 沒有別的 service 仍依賴將被 drop 的 column / table

### 5.2 執行
```bash
export AETHERCARE_DB_URL=jdbc:postgresql://prod-db:5432/aethercare
export AETHERCARE_DB_USER=aethercare_admin    # 需要 DDL 權限
export AETHERCARE_DB_PASSWORD=<from-vault>

./scripts/liquibase-rollback.sh history       # 確認 target tag 存在
./scripts/liquibase-rollback.sh tag <target>  # 真實 rollback
./scripts/liquibase-rollback.sh status        # 確認狀態
```

### 5.3 後置驗證
1. Application 先 deploy 對應舊版本（schema 不對齊新版會炸）
2. 跑 smoke test：`/actuator/health`、demo curl
3. 監控 5 分鐘確認 Prometheus / log 無異常
4. 解除 read-only / 恢復 replica 數

---

## 6. PG primary / replica 注意

- Logical replication slot（Debezium CDC 用）會被 schema rollback 影響：
  - drop 的 table 仍在 WAL → Debezium 嘗試 decode → 失敗
  - **SOP**：rollback 前先 stop Debezium connector，rollback 後重建 connector with `snapshot.mode=never` 從 WAL latest 開始
- Streaming replica（read-only follower）會自動跟主庫 schema：
  - 需要等 replica 同步完才能放出讀流量

---

## 7. 不能 rollback 的情況

- 若 changeset 含 `<sql>` 執行了不可逆 transformation（例：DELETE 業務資料、或 type conversion 損失精度），rollback 不會復原資料
- 解法：每個 destructive changeset 都顯式寫 rollback 段恢復資料；或 backup + restore

---

## 8. CI/CD 整合（建議）

**GitHub Actions 範例**：
```yaml
- name: Liquibase rollback (manual approval)
  run: ./scripts/liquibase-rollback.sh tag ${{ github.event.inputs.target_tag }}
  env:
    AETHERCARE_DB_URL: ${{ secrets.PROD_DB_URL }}
    AETHERCARE_DB_USER: ${{ secrets.PROD_DB_ADMIN_USER }}
    AETHERCARE_DB_PASSWORD: ${{ secrets.PROD_DB_ADMIN_PASSWORD }}
```

**k8s init container 範例**（每次 deploy 順便檢查 / 補 tag）：
```yaml
initContainers:
  - name: liquibase-validate
    image: liquibase/liquibase:4.31
    command:
      - "liquibase"
      - "--changeLogFile=/changelog/db.changelog-master.yaml"
      - "validate"
    volumeMounts:
      - name: changelog
        mountPath: /changelog
```

---

## 9. 故障排除

| 問題 | 解法 |
|---|---|
| `Cannot connect to the Docker daemon` | 啟動 Docker Desktop |
| `host.docker.internal` 連不到 | Linux host 加 `--add-host=host.docker.internal:host-gateway`（script 已含） |
| `rollback validation failed: missing rollback section` | 該 changeset 沒寫 rollback 也無法 auto-derive；手動補 SQL |
| Debezium 接到 WAL 看到 dropped table 失敗 | 先 DELETE connector，rollback，再 POST 新 connector |
| Liquibase lock 卡住（前次 rollback 中斷） | `liquibase release-locks` |

---

## 10. 相關檔案

- `aethercare-api/src/main/resources/db/changelog/db.changelog-master.yaml`
- `aethercare-api/src/main/resources/db/changelog/changes/`
- `scripts/liquibase-rollback.sh`
- 系統設計：`docs/system_design/aethercare_codex_system_design.md`
