# AetherCare TLS / Secrets 部署 Runbook

> 目標讀者：MVP 後期 → Production 上線負責人
>
> 適用版本：AetherCare API 0.0.1-SNAPSHOT（Spring Boot 3.5.14、Java 21）
>
> 相關檔案：
> - `aethercare-api/src/main/resources/application.yml`（default）
> - `aethercare-api/src/main/resources/application-vault.yml`（Vault profile）
> - `aethercare-api/src/main/resources/application-tls.yml`（TLS profile）
> - `docker-compose.yml`（含 vault dev service）
> - `docker-compose.tls.yml`（TLS override）
> - `scripts/seed-vault-secrets.sh`、`scripts/gen-dev-certs.sh`

---

## 1. 總覽：MVP vs Production 差異

| 項目 | MVP（現況） | 中期（Beta） | Production |
|---|---|---|---|
| Secret 來源 | env vars + 程式碼 default | HashiCorp Vault dev mode | Vault HA cluster / AWS Secrets Manager / GCP Secret Manager |
| Secret 注入 | docker-compose / shell export | Spring Cloud Vault（KV v2） | Vault AppRole + lease renewal，或 ExternalSecrets operator |
| TLS（PG/Redis/Kafka） | 明文（docker network 內 OK） | self-signed CA（mkcert） | 私有 CA / cert-manager 自動簽發 |
| TLS（HTTP API） | 明文 8080 | self-signed 8443 | TLS 終結於 ingress / ALB（Let's Encrypt） |
| Secret rotation | 手動重啟 | actuator `/refresh` + Vault dynamic creds | 自動輪替 + zero-downtime reload |
| 觀測性 | actuator | actuator + Vault audit log | + SIEM / Vault telemetry → Prometheus |

---

## 2. Secrets Management

### 2.1 本機開發（MVP 現況）

- 直接走 `application.yml` 的 env var default：
  ```bash
  AETHERCARE_DB_PASSWORD=aethercare \
  AETHERCARE_JWT_SECRET=<base64> \
  ./gradlew bootRun
  ```
- 不啟用任何 Vault profile，行為不變。

### 2.2 中期：HashiCorp Vault（已整合）

**啟動 dev Vault：**

```bash
docker compose up -d vault
./scripts/seed-vault-secrets.sh
```

**啟用 vault profile 跑 app：**

```bash
SPRING_PROFILES_ACTIVE=local,vault \
AETHERCARE_VAULT_URI=http://localhost:8200 \
AETHERCARE_VAULT_TOKEN=dev-only-root-token \
./gradlew bootRun
```

Spring Cloud Vault 會在啟動時對 `secret/aethercare` 取出所有 key/value，
直接合併進 Spring Environment，覆蓋 `application.yml` 同名 property。

**Vault 內預期 key：**

| Vault key | 對應 Spring property |
|---|---|
| `spring.datasource.password` | DB password |
| `aethercare.security.jwt.secret` | JWT 簽章 secret |
| `aethercare.kafka.sasl.password` | Kafka SASL（若啟用 SASL） |

**驗證已從 Vault 載入：**

```bash
curl -s http://localhost:8080/actuator/env/aethercare.security.jwt.secret \
  | jq '.propertySources[] | select(.name | contains("vault"))'
```

### 2.3 Production：建議路徑

| 雲端 | 推薦方案 | 切換方式 |
|---|---|---|
| AWS | Secrets Manager + IRSA | 改 `spring.config.import: aws-secretsmanager:aethercare/` 並加依賴 `spring-cloud-aws-starter-secrets-manager` |
| GCP | Secret Manager + Workload Identity | 加依賴 `spring-cloud-gcp-starter-secretmanager`，property 用 `${sm://projects/.../secrets/jwt-secret}` |
| Self-hosted k8s | Vault HA + ExternalSecrets | 不改 app 設定；ExternalSecrets 把 Vault 同步成 k8s Secret，再 mount 為 env var |
| 純 Vault | Vault HA + AppRole | 把 `application-vault.yml` 內 `authentication: TOKEN` 改成 `APPROLE` 並注入 `VAULT_ROLE_ID` / `VAULT_SECRET_ID` |

**推薦：Vault HA + ExternalSecrets**

理由：
1. App 端設定不變（仍讀 env var），降低 production / staging 環境差異。
2. 集中 audit、易於合規。
3. ExternalSecrets 自帶 reconciliation（Vault 改了 → k8s Secret 自動同步）。

### 2.4 Vault dev → prod 遷移路徑

1. **stage 1**：dev mode（現況）+ root token，僅 `secret/aethercare` 一個 path。
2. **stage 2**：HA mode（3 nodes raft）+ AppRole，policy 限制 `path "secret/data/aethercare/*" { capabilities = ["read"] }`。
3. **stage 3**：dynamic credentials（DB / Kafka）— Vault 為每個 app instance 動態產生短 TTL 的 DB password，搭配 Spring Cloud Vault 的 `spring.cloud.vault.database.*`。
4. **stage 4**：Transit secrets engine 做 envelope encryption（PII 欄位加密）。

### 2.5 Secret rotation 策略

| Secret | 頻率 | 流程 |
|---|---|---|
| JWT secret | 90 天 | 1) Vault 寫新 key（保留舊的 `*.previous`）2) app `/actuator/refresh` 3) JwtFilter 同時驗證 new+previous，4) 7 天後刪除 previous |
| DB password | 30 天（或 dynamic） | Vault DB secret engine 自動旋轉，連線字串用 `${spring.datasource.password}`，refresh 即可 |
| Kafka SASL | 90 天 | Vault 寫新值 → ExternalSecrets 同步 → rolling restart |
| TLS cert | 30 天 | cert-manager auto-renew，spring SSL bundle reload-on-update（3.2+） |

---

## 3. TLS

### 3.1 本機開發：明文

docker-compose 內部 network 隔離，直接走明文。

### 3.2 中期：self-signed cert（已整合）

```bash
brew install mkcert openssl
./scripts/gen-dev-certs.sh
docker compose -f docker-compose.yml -f docker-compose.tls.yml up -d
SPRING_PROFILES_ACTIVE=local,tls ./gradlew bootRun
```

腳本產出（`scripts/dev-certs/`）：
- `aethercare.crt` / `aethercare.key`：PEM cert + key
- `truststore.p12`：含 mkcert root CA（給 Spring / Kafka 信任 self-signed）
- `keystore.p12`：給 Kafka SSL listener / mTLS client

`application-tls.yml` 透過 SSL Bundle 統一管理，主要 endpoint：
- HTTPS API：`https://localhost:8443`
- PG：`jdbc:postgresql://localhost:15432/aethercare?sslmode=verify-full&...`
- Redis：`localhost:16380`（TLS port）
- Kafka：`localhost:9093`（SSL listener）

### 3.3 Production：真實 CA

| 範圍 | CA |
|---|---|
| 公開 HTTPS endpoint | Let's Encrypt（cert-manager + ACME HTTP-01） |
| Internal mesh（API → DB / Redis / Kafka） | 私有 CA（cert-manager 自簽 root，或 Vault PKI engine） |
| Client mTLS（B2B 整合） | 公開 CA（如 DigiCert）或互換私 CA |

**強烈推薦**：Vault PKI secrets engine——同一套 Vault 既給 secrets 又簽 cert，
auth、audit、rotation 一致。

### 3.4 Cert 輪替

- **零停機方案**：Spring Boot 3.2+ 的 SSL Bundle 支援 `reload-on-update`，
  cert 檔案被替換時自動重新載入，不需重啟 process。
- **k8s 環境**：
  1. cert-manager 寫入 k8s Secret。
  2. Pod mount Secret 為 file。
  3. reloader controller 偵測 Secret 變更 → 觸發 rolling restart（保險路徑）。
- **actuator `/refresh`**：用於需要 refresh 整個 Spring context 的情境（搭配
  `spring-cloud-context`），對 SSL Bundle 已非必要。

---

## 4. k8s 部署 hint

```yaml
# external-secret.yaml（簡化）
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: aethercare-secrets
spec:
  refreshInterval: 1h
  secretStoreRef:
    name: vault-backend
    kind: ClusterSecretStore
  target:
    name: aethercare-secrets
    creationPolicy: Owner
  data:
    - secretKey: AETHERCARE_DB_PASSWORD
      remoteRef:
        key: secret/data/aethercare
        property: spring.datasource.password
    - secretKey: AETHERCARE_JWT_SECRET
      remoteRef:
        key: secret/data/aethercare
        property: aethercare.security.jwt.secret
```

```yaml
# deployment.yaml 片段
spec:
  template:
    spec:
      containers:
        - name: aethercare-api
          image: ghcr.io/lza/aethercare-api:0.0.1
          envFrom:
            - secretRef:
                name: aethercare-secrets
          volumeMounts:
            - name: tls-certs
              mountPath: /etc/aethercare/certs
              readOnly: true
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod,tls"
            - name: AETHERCARE_TLS_CERT_DIR
              value: "/etc/aethercare/certs"
      volumes:
        - name: tls-certs
          secret:
            secretName: aethercare-tls-cert     # cert-manager 產出
```

要點：
- App 不直接打 Vault，由 ExternalSecrets 反映成 k8s Secret，責任分離。
- TLS cert 走 k8s Secret + volume mount，搭配 SSL Bundle 自動 reload。
- `SPRING_PROFILES_ACTIVE=prod,tls`：prod 會疊在 default 上，tls 提供 SSL Bundle。

---

## 5. 連線設定 Cheatsheet（Production-grade env vars）

### PostgreSQL

```bash
AETHERCARE_DB_URL='jdbc:postgresql://pg-primary.svc:5432/aethercare?sslmode=verify-full&sslrootcert=/etc/aethercare/certs/pg-ca.crt&ApplicationName=aethercare-api'
AETHERCARE_DB_USER=aethercare_app
AETHERCARE_DB_PASSWORD=<from-vault>
```

### Redis

```bash
AETHERCARE_REDIS_HOST=redis-master.svc
AETHERCARE_REDIS_PORT=6380
SPRING_DATA_REDIS_SSL_ENABLED=true
SPRING_DATA_REDIS_PASSWORD=<from-vault>
```

### Kafka

```bash
AETHERCARE_KAFKA_BOOTSTRAP=kafka-bootstrap.svc:9093
SPRING_KAFKA_SECURITY_PROTOCOL=SASL_SSL
SPRING_KAFKA_PROPERTIES_SASL_MECHANISM=SCRAM-SHA-512
SPRING_KAFKA_PROPERTIES_SASL_JAAS_CONFIG='org.apache.kafka.common.security.scram.ScramLoginModule required username="aethercare" password="${vault-injected}";'
SPRING_KAFKA_SSL_TRUST_STORE_LOCATION=file:/etc/aethercare/certs/truststore.p12
SPRING_KAFKA_SSL_TRUST_STORE_PASSWORD=<from-vault>
SPRING_KAFKA_SSL_TRUST_STORE_TYPE=PKCS12
```

### Vault

```bash
AETHERCARE_VAULT_URI=https://vault.svc:8200
SPRING_CLOUD_VAULT_AUTHENTICATION=KUBERNETES
SPRING_CLOUD_VAULT_KUBERNETES_ROLE=aethercare
SPRING_CLOUD_VAULT_KUBERNETES_SERVICE_ACCOUNT_TOKEN_FILE=/var/run/secrets/kubernetes.io/serviceaccount/token
SPRING_CLOUD_VAULT_FAIL_FAST=true        # production 應 fail-fast
SPRING_CLOUD_VAULT_CONFIG_LIFECYCLE_ENABLED=true   # 啟用 lease renewal
```

---

## 6. Smoke Test 清單

部署後逐項檢查：

```bash
# 1. App 起來
curl -fsk https://api.aethercare/actuator/health | jq '.status'      # UP

# 2. DB TLS handshake OK
psql "host=pg.svc port=5432 dbname=aethercare user=postgres sslmode=verify-full" -c "SELECT ssl_is_used();"

# 3. Redis TLS
redis-cli -h redis.svc -p 6380 --tls --cacert /etc/aethercare/certs/redis-ca.crt PING

# 4. Kafka SSL
kafka-topics --bootstrap-server kafka.svc:9093 --command-config client.properties --list

# 5. Vault 路徑可達
curl -fs -H "X-Vault-Token: $TOKEN" https://vault.svc:8200/v1/secret/data/aethercare | jq '.data.data | keys'

# 6. Spring 確認 secret 來源
curl -fsk https://api.aethercare/actuator/env/aethercare.security.jwt.secret \
  | jq '.propertySources[] | .name' | grep vault
```

---

## 7. 常見問題

**Q1. Vault dev mode 重啟資料就沒了？**
A：是的，dev mode 是 in-memory。production 用 raft / consul 後端。本 repo 為了 onboarding 簡單故意用 dev。

**Q2. mkcert root CA 在 docker container 內不被信任？**
A：把 `$(mkcert -CAROOT)/rootCA.pem` 額外 mount 進 container，或使用 `truststore.p12` 而非系統 trust store。

**Q3. Spring Cloud Vault 啟動時 timeout？**
A：本 repo 已設 `fail-fast: false`，Vault 不可達會 fallback 到 env var default。production 應改 `true`。

**Q4. JWT 輪替後舊 token 被拒？**
A：JwtFilter 必須同時驗證 new + previous secret 至少 1×TTL（本專案 1 小時），grace period 內舊 token 仍有效。

---

## 8. 參考

- [Spring Cloud Vault 4.1 docs](https://docs.spring.io/spring-cloud-vault/reference/4.1/)
- [Spring Boot 3.5 SSL Bundles](https://docs.spring.io/spring-boot/reference/features/ssl.html)
- [HashiCorp Vault production hardening](https://developer.hashicorp.com/vault/tutorials/operations/production-hardening)
- [cert-manager + Vault PKI](https://cert-manager.io/docs/configuration/vault/)
- [External Secrets Operator](https://external-secrets.io/)
