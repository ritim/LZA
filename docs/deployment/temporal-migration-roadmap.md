# Temporal Workflow Migration Roadmap

> Status: Draft（Phase 2 規劃文件）。對應介面 commit：
> `aethercare-api/src/main/java/com/lza/aethercare/workflow/engine/WorkflowEngine.java`

## 1. 為什麼要用 Temporal（vs 現行 @Scheduled scanner）

MVP 由 `CareWorkflowService` + `CareTaskService` + Postgres conditional update 組成
workflow runtime：

- `CareTaskTimeoutScanner @Scheduled(fixedDelay = 5s)` 撈 expired pending tasks → 觸發 escalation
- 狀態機由 `markTerminalIfIn` / `advanceLevel` native SQL 帶 allowed-status guard
- 互斥鎖由 Redis (`WorkflowLockService`) 提供 per-workflow 序列化

**痛點：**

| 限制 | 現況 | Temporal 解法 |
|---|---|---|
| Timeout 解析度 | scanner 5 秒一次，最差 5s 延遲 | timer 觸發精度 ms 級 |
| 跨節點協調 | 多個 API instance 同時跑 scanner，靠 Redis 鎖去重 | task queue 天然 single-consumer |
| Retry / backoff | 手寫 try / catch + audit STATE_CONFLICT_SKIPPED | activity 內建 exponential backoff + retry policy |
| Saga / 補償 | 沒有，只能靠 outbox + manual rollback | workflow code 自然表達補償流程 |
| Long-running workflow（如「24 小時觀察期」） | 整段塞 task table，scanner 反覆掃 | `Workflow.sleep(Duration.ofHours(24))` 直接休眠 |
| 可觀測性 | audit_log + Prom metrics | Temporal Web UI 直接看 workflow history |
| Replay debugging | 無，只能讀 audit_log 推測 | event sourcing replay，可重現任何過去執行 |

**何時值得遷移：**

- workflow 超過 3 個 timer / decision point（目前 MVP 只有 1 層 timeout，不急）
- 需要支援 1 小時以上的 sleep / 跨日 workflow
- 多 region 部署，需要跨 region durable execution
- task 量超過 10k/day，scanner 撈表延遲明顯

## 2. Migration 三階段

### Phase A：Extract `WorkflowEngine` interface（本 commit）

只新增介面，不動既有實作：

- `workflow/engine/WorkflowEngine.java`：四個 method 簽名
- 既有 `CareWorkflowService` 暫不 implements 此介面（避免大改 controller / event service 的注入）
- 此 commit 的價值在「凍結對外 contract」：未來新實作必須符合 `Long → void` 簽名與冪等假設

驗收：本介面有 javadoc + 對應 package-info，後續 PR 可基於此介面寫整合測試。

### Phase B：加 `TemporalWorkflowEngineImpl`

```
aethercare-api/build.gradle.kts:
  implementation("io.temporal:temporal-sdk:1.30.+")
  implementation("io.temporal:temporal-spring-boot-starter:1.30.+")
```

- 新增 `workflow/engine/temporal/CareTemporalWorkflow.java`（@WorkflowInterface）
- 新增 `workflow/engine/temporal/CareTemporalActivities.java`：把
  `CareTaskService.createTask` / `NotificationService.notify` /
  `CareAuditService.log` 包成 activity
- 新增 `workflow/engine/temporal/TemporalWorkflowEngineImpl implements WorkflowEngine`：
  接 `WorkflowClient` 啟 workflow / signal / query
- 新增 `workflow/engine/scheduled/ScheduledWorkflowEngineImpl implements WorkflowEngine`：
  delegate 到既有 `CareWorkflowService`（adapter pattern，避免大改）
- 由 `@ConditionalOnProperty(name = "aethercare.workflow.engine", havingValue = "temporal")`
  選擇 bean，預設 `scheduled`

### Phase C：切流量 + 監控

- 加 feature flag `aethercare.workflow.engine.shadow=true`：兩條路一起跑，比對結果寫到
  `care_audit_log.action=ENGINE_SHADOW_DIFF`
- 灰度（環境變數逐個 region / 逐個 elderId hash 切換）：
  - dev → staging 100% temporal
  - production 1% → 10% → 50% → 100%
- 觀察 SLA dashboard：`avg_resolve_seconds` / `escalation_rate` 不能變差
- 觀察 Temporal Web UI 的 `workflow_failed` / `workflow_timeout`
- 全量切換後，下一個 release 移除 ScheduledWorkflowEngineImpl + scanner

### Rollback：`profile=temporal-disabled` fallback 到 @Scheduled scanner

```bash
# 緊急 rollback（無需 redeploy，只要改 config + restart pod）
AETHERCARE_WORKFLOW_ENGINE=scheduled \
SPRING_PROFILES_ACTIVE=local,temporal-disabled \
  ./gradlew bootRun
```

`temporal-disabled` profile 會：

- 跳過 Temporal `Worker` 註冊（避免空 namespace 造成 startup fail）
- 啟用 `ScheduledWorkflowEngineImpl` bean（即原本的 `CareWorkflowService` adapter）
- 已在 Temporal 上跑的 workflow 由 fallback worker 標記為 manual handoff（audit
  log 記 `ENGINE_FALLBACK_HANDOFF`），由 ops 手動 resolve 或重啟成 scheduled engine

## 3. Sample Temporal workflow code（純 markdown 範例）

```java
// CareTemporalWorkflow.java
@WorkflowInterface
public interface CareTemporalWorkflow {
    @WorkflowMethod
    void run(Long eventId, Long elderId);

    @SignalMethod
    void confirmSafe(Long actorId);

    @SignalMethod
    void needHelp(Long actorId);

    @QueryMethod
    String currentStatus();
}
```

```java
// CareTemporalWorkflowImpl.java
public class CareTemporalWorkflowImpl implements CareTemporalWorkflow {

    private final CareTemporalActivities activities = Workflow.newActivityStub(
            CareTemporalActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    private volatile boolean confirmed = false;
    private volatile boolean needHelp = false;
    private int currentLevel = 1;

    @Override
    public void run(Long eventId, Long elderId) {
        Long workflowId = activities.startWorkflow(eventId, elderId);

        while (currentLevel <= 3) {
            ContactInfo c = activities.findContact(elderId, currentLevel);
            if (c == null) {
                activities.markUnresolved(workflowId,
                        "已無 level=" + currentLevel + " 聯絡人");
                return;
            }
            Long taskId = activities.createTask(workflowId, eventId, c, currentLevel);
            activities.notifyContact(taskId, elderId);

            // 等 SLA 秒數，期間若收到 signal 立即跳出
            boolean responded = Workflow.await(
                    Duration.ofSeconds(c.slaSeconds()),
                    () -> confirmed || needHelp);

            if (confirmed) {
                activities.confirmSafe(workflowId, /*actor*/ null);
                return;
            }
            if (needHelp || !responded) {
                activities.markTaskTimeout(taskId);
                currentLevel++;
            }
        }
        activities.markUnresolved(workflowId, "已耗盡所有層級");
    }

    @Override public void confirmSafe(Long actorId) { confirmed = true; }
    @Override public void needHelp(Long actorId)    { needHelp = true; }
    @Override public String currentStatus() {
        return confirmed ? "RESOLVED" : "WAITING_LEVEL_" + currentLevel;
    }
}
```

```java
// TemporalWorkflowEngineImpl.java
@Service
@ConditionalOnProperty(name = "aethercare.workflow.engine", havingValue = "temporal")
@RequiredArgsConstructor
public class TemporalWorkflowEngineImpl implements WorkflowEngine {

    private final WorkflowClient client;
    private static final String QUEUE = "care-workflow-queue";

    @Override
    public Long startWorkflow(Long eventId, Long elderId) {
        CareTemporalWorkflow stub = client.newWorkflowStub(
                CareTemporalWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(QUEUE)
                        .setWorkflowId("care-" + eventId)  // dedup by eventId
                        .build());
        WorkflowClient.start(stub::run, eventId, elderId);
        return eventId;  // workflow id 對應 eventId（1:1）
    }

    @Override
    public void confirmSafe(Long workflowId, Long actorId) {
        client.newWorkflowStub(CareTemporalWorkflow.class, "care-" + workflowId)
              .confirmSafe(actorId);
    }
    // ...
}
```

## 4. K8s 部署（temporalio Helm chart）

Strimzi-style operator 不適用 Temporal（Temporal 自己的 server 用 Cassandra /
PostgreSQL，不像 Kafka 有專屬 operator）。建議：

```bash
helm repo add temporal https://go.temporal.io/helm-charts
helm install temporal temporal/temporal \
  --set server.replicaCount=3 \
  --set cassandra.config.cluster_size=3 \
  --set elasticsearch.replicas=3 \
  --set prometheus.enabled=false \
  --set grafana.enabled=false
```

或用 [Temporal Cloud](https://temporal.io/cloud) 託管，省掉 Cassandra 維運。

API pod 改為跑 worker（Temporal SDK 在同一個 JVM）：

```yaml
# k8s/aethercare-api-deployment.yaml
spec:
  template:
    spec:
      containers:
        - name: api
          env:
            - name: TEMPORAL_NAMESPACE
              value: aethercare-prod
            - name: TEMPORAL_FRONTEND_ADDR
              value: temporal-frontend.temporal.svc.cluster.local:7233
            - name: AETHERCARE_WORKFLOW_ENGINE
              value: temporal
```

Worker scale-out：另開一個 Deployment `aethercare-worker`（同 image，不開 8080
HTTP port），horizontal autoscale 依 Temporal SDK metrics
（`temporal_workflow_task_queue_poll_succeed_per_ts`）。

## 5. Cost estimate

| 方案 | 月成本（10k workflows/day） | 維運負擔 |
|---|---|---|
| 現行 @Scheduled scanner | ~$0（用既有 PG / Redis） | 低 |
| Temporal Cloud | ~$0.5 / 1k actions × 30k/day = ~$450/mo | 極低（Temporal 顧 server） |
| Self-hosted Temporal + Cassandra | ~$300/mo (3-node Cassandra m5.large + Temporal frontend) | 高（Cassandra 維運痛點：repair, snapshot） |

> 假設：10k workflow start/day × 平均 5 actions/workflow = 50k actions/day。

決策：MVP 階段不上 Temporal；客戶量 > 1k elders 或 SLA 要求 < 1 秒 timeout 時再切。

## 6. Trade-offs

| 面向 | 現行 | Temporal | 變化 |
|---|---|---|---|
| Durability | DB row + audit_log | event-sourced workflow history | ↑↑ |
| Timeout 精度 | 5 秒 (scanner interval) | ms 級 timer | ↑ |
| 維運複雜度 | 1 個 service + Redis | 多 Cassandra/ES + Temporal frontend | ↓↓ |
| 開發體感 | controller 直呼 service | 需理解 workflow vs activity / determinism | ↓ |
| 月成本 | ~$0 | $300-450/mo | ↓↓ |
| Replay debug | audit log 推測 | Web UI 點 workflow id 直接看 history | ↑↑ |
| Saga 支援 | 手寫 outbox + 補償 | workflow code 自然表達 | ↑ |

## 7. Rollback 詳細

| 觸發條件 | 動作 |
|---|---|
| Temporal Cloud SLA 違約（連續 5 分鐘 unavailable） | helm chart 起 self-hosted backup namespace；切 `TEMPORAL_FRONTEND_ADDR` |
| Workflow history corruption（event sourcing 失敗） | profile=temporal-disabled，當下進行中的 workflow 寫成 `UNRESOLVED reason="engine-fallback"`，由 ops 手動處理 |
| 成本超預算 | 把長尾低風險 workflow（LOW risk + 無 escalation）切回 @Scheduled，HIGH risk 留 Temporal |
| 灰度發現 SLA 變差 | 立即把 `aethercare.workflow.engine` 改回 `scheduled`，下次新 workflow 走舊路徑；舊 workflow 等自然結案 |

Rollback 不需 schema 變更（Temporal 自己的 DB 與 aethercare PG 隔離），只要切
config + restart 即可，回收 Temporal 資源由 helm uninstall 或 Temporal Cloud
namespace 退訂。
