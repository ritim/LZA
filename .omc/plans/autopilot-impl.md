# AetherCare AI MVP — Autopilot Implementation Plan

> 本 plan 直接對應系統設計文件 `docs/system_design/aethercare_codex_system_design.md` 與 `.omc/autopilot/spec.md`，executor 可依「執行批次」順序逐項落地。技術棧已凍結：Java 21 (LTS) / Spring Boot 3.5.14 / Gradle KTS / PostgreSQL 16 / Redis 7 / Kafka 3.7 KRaft / Liquibase / Testcontainers / Lombok。（原規劃 Java 25，因 Gradle 8.14 Kotlin DSL 內嵌 Kotlin compiler 不識別 Java 25，daemon 與 toolchain 都改用 Java 21；待 Gradle 9 / Spring Boot 4 再升）

---

## 0. 全域約定

- Base package：`com.lza.aethercare`
- Sub-packages（domain-first）：`event`、`decision`、`workflow`、`task`、`notification`、`action`、`audit`、`userprofile`、`common`
- 子 sub-packages 規約：每個 domain 內部使用 `entity` / `repository` / `service` / `controller` / `dto` / `enums` / `event`（Kafka payload）
- 所有時間：DB 使用 `TIMESTAMPTZ`，Java 使用 `OffsetDateTime`，Hibernate `jdbc.time_zone=Asia/Taipei`
- 所有 ID：`BIGSERIAL` (PostgreSQL) → Java `Long`
- 所有 enum：以 `STRING` 方式持久化（`@Enumerated(EnumType.STRING)`）
- 所有 Service 寫入路徑必有 `@Transactional`，讀取路徑使用 `@Transactional(readOnly = true)`
- Lombok：可用 `@Getter`、`@Setter`、`@Builder`、`@NoArgsConstructor`、`@AllArgsConstructor`、`@RequiredArgsConstructor`、`@Slf4j`
- 例外處理：自訂 `BusinessException` + `ErrorCode` enum，由 `GlobalExceptionHandler` 轉成 `ProblemDetail`
- Bean Validation：所有 request DTO `@Valid`
- 所有 Kafka payload：immutable `record`，JSON 序列化使用 Jackson（topic value 為 String JSON）

---

## 1. 完整檔案樹

### 1.1 新增 / 修改：根目錄與基礎設施（A 模組）

```
/Users/yao/0.Projects/LZA/.gitignore                                  [新增]
/Users/yao/0.Projects/LZA/README.md                                   [新增]
/Users/yao/0.Projects/LZA/docker-compose.yml                          [新增]
/Users/yao/0.Projects/LZA/aethercare-api/README.md                    [新增]
/Users/yao/0.Projects/LZA/aethercare-api/build.gradle.kts             [修改：補測試/工具依賴]
/Users/yao/0.Projects/LZA/aethercare-api/src/main/resources/application.yml          [修改：補 jackson / kafka topic 設定]
/Users/yao/0.Projects/LZA/aethercare-api/src/main/resources/application-test.yml     [新增：Testcontainers 用]
```

### 1.2 Liquibase changesets（B 模組）

```
.../resources/db/changelog/changes/0001-create-care-event.yaml
.../resources/db/changelog/changes/0002-create-care-workflow-instance.yaml
.../resources/db/changelog/changes/0003-create-care-task.yaml
.../resources/db/changelog/changes/0004-create-care-action.yaml
.../resources/db/changelog/changes/0005-create-care-audit-log.yaml
.../resources/db/changelog/changes/0006-create-care-contact-escalation.yaml
.../resources/db/changelog/changes/0007-seed-demo-data.yaml          [demo 用：1 位長者 + 2 位聯絡人]
```

### 1.3 Java source（C–K 模組）

```
src/main/java/com/lza/aethercare/
├── AethercareApiApplication.java                                    [修改：加 @EnableScheduling, @EnableKafka]
├── package-info.java                                                [新增]
│
├── common/
│   ├── package-info.java
│   ├── config/JacksonConfig.java
│   ├── config/RedisConfig.java
│   ├── config/KafkaTopicConfig.java
│   ├── config/SchedulingConfig.java
│   ├── error/ErrorCode.java
│   ├── error/BusinessException.java
│   ├── error/GlobalExceptionHandler.java
│   ├── time/Clock.java
│   └── time/SystemClock.java
│
├── userprofile/
│   ├── package-info.java
│   ├── entity/CareContactEscalation.java
│   ├── enums/NotificationChannel.java
│   ├── repository/CareContactEscalationRepository.java
│   └── service/EscalationContactService.java
│
├── event/
│   ├── package-info.java
│   ├── entity/CareEvent.java
│   ├── enums/CareEventStatus.java
│   ├── enums/CareEventSource.java
│   ├── enums/CareEventType.java
│   ├── enums/RiskLevel.java
│   ├── repository/CareEventRepository.java
│   ├── service/CareEventService.java
│   ├── dto/CreateCareEventRequest.java
│   ├── dto/CareEventResponse.java
│   ├── controller/CareEventController.java
│   └── event/CareEventCreatedMessage.java
│
├── decision/
│   ├── package-info.java
│   ├── service/DecisionService.java
│   └── service/RuleBasedRiskClassifier.java
│
├── workflow/
│   ├── package-info.java
│   ├── entity/CareWorkflowInstance.java
│   ├── enums/CareWorkflowStatus.java
│   ├── enums/CareWorkflowType.java
│   ├── repository/CareWorkflowInstanceRepository.java
│   ├── service/CareWorkflowService.java
│   ├── service/WorkflowLockService.java
│   ├── dto/WorkflowResponse.java
│   ├── controller/WorkflowController.java
│   └── event/CareWorkflowStartedMessage.java
│
├── task/
│   ├── package-info.java
│   ├── entity/CareTask.java
│   ├── enums/CareTaskStatus.java
│   ├── enums/AssigneeType.java
│   ├── repository/CareTaskRepository.java
│   ├── service/CareTaskService.java
│   ├── service/CareTaskTimeoutScanner.java
│   └── event/CareTaskCreatedMessage.java
│
├── action/
│   ├── package-info.java
│   ├── entity/CareAction.java
│   ├── enums/CareActionType.java
│   ├── repository/CareActionRepository.java
│   ├── service/CareActionService.java
│   ├── dto/CreateCareActionRequest.java
│   ├── dto/CareActionResponse.java
│   ├── controller/CareActionController.java
│   └── event/CareActionReceivedMessage.java
│
├── notification/
│   ├── package-info.java
│   ├── service/NotificationService.java
│   ├── service/StubNotificationGateway.java
│   └── event/CareNotificationSentMessage.java
│
└── audit/
    ├── package-info.java
    ├── entity/CareAuditLog.java
    ├── enums/CareAuditAction.java
    ├── repository/CareAuditLogRepository.java
    ├── service/CareAuditService.java
    ├── dto/AuditLogResponse.java
    ├── controller/CareAuditController.java
    └── event/CareAuditCreatedMessage.java
```

### 1.4 Test 樹（L 模組）

```
src/test/java/com/lza/aethercare/
├── AethercareApiApplicationTests.java
│
├── decision/RuleBasedRiskClassifierTest.java
├── workflow/CareWorkflowServiceTest.java
├── task/CareTaskTimeoutScannerTest.java
├── action/CareActionServiceTest.java
├── audit/CareAuditServiceTest.java
├── notification/NotificationServiceTest.java
│
└── integration/
    ├── AbstractIntegrationTest.java
    └── FallDetectedEndToEndIT.java

src/test/resources/
├── application-test.yml
└── junit-platform.properties
```

---

## 2. 執行批次

**Batch 0（serial bootstrap）**
1. build.gradle.kts 補 testcontainers + redis testcontainer + awaitility
2. application.yml 補 aethercare 自訂設定
3. application-test.yml
4. docker-compose.yml + .gitignore + 兩個 README
5. AethercareApiApplication.java 加 @EnableScheduling, @EnableKafka
6. 9 個 package-info.java

**Batch 1（parallel）**
- 1A：Liquibase 0001–0007
- 1B：所有 enum
- 1C：common/error/* + common/time/* + 4 個 config
- 1D：6 個 Kafka payload record

**Batch 2（parallel，依賴 Batch 1）**
- 2A：6 個 Entity
- 2B：所有 DTO

**Batch 3（parallel，依賴 Batch 2）**
- 3A：6 個 Repository
- 3B：DecisionService + RuleBasedRiskClassifier
- 3C：StubNotificationGateway

**Batch 4（serial 業務組裝）**
1. CareAuditService → 2. EscalationContactService → 3. WorkflowLockService
4. NotificationService → 5. CareTaskService → 6. CareWorkflowService
7. CareActionService → 8. CareEventService → 9. CareTaskTimeoutScanner

**Batch 5（parallel）**
- 5A：4 個 Controller + GlobalExceptionHandler
- 5B：6 個 service unit test

**Batch 6（serial）**
1. AbstractIntegrationTest → 2. FallDetectedEndToEndIT
3. ./gradlew clean build → 4. docker compose 啟動 + curl 驗收

---

## 3. 關鍵設計細節

### 3.1 狀態機（集中管理）

```java
// task/enums/CareTaskStatus.java
public enum CareTaskStatus {
    PENDING, ACKNOWLEDGED, COMPLETED, TIMEOUT, CANCELLED;
    private static final Map<CareTaskStatus, Set<CareTaskStatus>> TRANSITIONS = Map.of(
        PENDING,      EnumSet.of(ACKNOWLEDGED, COMPLETED, TIMEOUT, CANCELLED),
        ACKNOWLEDGED, EnumSet.of(COMPLETED, TIMEOUT, CANCELLED),
        COMPLETED,    EnumSet.noneOf(CareTaskStatus.class),
        TIMEOUT,      EnumSet.noneOf(CareTaskStatus.class),
        CANCELLED,    EnumSet.noneOf(CareTaskStatus.class)
    );
    public boolean canTransitionTo(CareTaskStatus next) {
        return TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }
    public boolean isTerminal() { return TRANSITIONS.get(this).isEmpty(); }
}

// workflow/enums/CareWorkflowStatus.java
public enum CareWorkflowStatus {
    NEW, ACTIVE, WAITING_RESPONSE, ACKNOWLEDGED, ESCALATED, RESOLVED, UNRESOLVED;
    // TRANSITIONS map 同上
}
```

### 3.2 Conditional Update SQL（文件 13.1, 13.2）

```java
// CareTaskRepository
@Modifying
@Query("""
    update CareTask t
       set t.status = com.lza.aethercare.task.enums.CareTaskStatus.TIMEOUT,
           t.updatedAt = :now,
           t.version = t.version + 1
     where t.id = :id
       and t.status = com.lza.aethercare.task.enums.CareTaskStatus.PENDING
       and t.deadlineAt < :now
    """)
int markTimeoutIfPending(@Param("id") Long id, @Param("now") OffsetDateTime now);

@Modifying
@Query("""
    update CareTask t
       set t.status = com.lza.aethercare.task.enums.CareTaskStatus.COMPLETED,
           t.completedAt = :now, t.updatedAt = :now, t.version = t.version + 1
     where t.id = :id
       and t.status in (
            com.lza.aethercare.task.enums.CareTaskStatus.PENDING,
            com.lza.aethercare.task.enums.CareTaskStatus.ACKNOWLEDGED)
    """)
int completeIfActive(@Param("id") Long id, @Param("now") OffsetDateTime now);
```

| affected rows | 處理 |
|---|---|
| 1 | 進下一步（escalate / resolve） |
| 0 | scanner 路徑 → log 跳過；API 路徑 → 拋 BusinessException → HTTP 409 |

### 3.3 Kafka Topic Producer 接線

| Topic | Producer 位置 |
|---|---|
| care.event.created | CareEventService.createAndStartWorkflow |
| care.workflow.started | CareWorkflowService.start |
| care.task.created | CareTaskService.createTask |
| care.notification.sent | NotificationService.notify |
| care.action.received | CareActionService.handle |
| care.audit.created | CareAuditService.log |

`KafkaTemplate<String,String>`，value=`ObjectMapper.writeValueAsString(record)`，key=eventId/workflowId。
Sample consumer 在 `notification.SampleAuditConsumer` 監聽 `care.audit.created`。

### 3.4 Redis Keys

| Key | TTL | 用途 |
|---|---|---|
| workflow:lock:{id} | 300s | escalate / resolve 入口 SETNX |
| elder:latest-status:{id} | 3600s | dashboard 快查 |

### 3.5 Liquibase Schema 重點

| Table | 主要欄位 | Index |
|---|---|---|
| care_event | metadata JSONB, occurred_at TIMESTAMPTZ | (elder_id, occurred_at desc), (status) |
| care_workflow_instance | event_id FK, version | (event_id), (elder_id, status) |
| care_task | deadline_at, status, version | (status, deadline_at) — scanner 熱路徑 |
| care_action | task_id FK, workflow_id FK | (workflow_id), (task_id) |
| care_audit_log | action, message | (workflow_id, created_at) |
| care_contact_escalation | level, channel, sla_seconds | UNIQUE (elder_id, level) |

`0007-seed-demo-data.yaml` 插入：
- elder=1001, contact=2001, level=1, channel=LINE, sla=30
- elder=1001, contact=2002, level=2, channel=SMS,  sla=60

### 3.6 docker-compose.yml

- postgres:16-alpine（5432, healthcheck pg_isready）
- redis:7-alpine（6379, healthcheck redis-cli ping）
- confluentinc/cp-kafka:7.7.1（KRaft, 9092, healthcheck kafka-topics --list）
- volumes: pgdata, kafka-data

### 3.7 build.gradle.kts 補

```kotlin
testImplementation("org.testcontainers:junit-jupiter")
testImplementation("org.testcontainers:postgresql")
testImplementation("org.testcontainers:kafka")
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("com.redis:testcontainers-redis:2.2.2")
testImplementation("org.awaitility:awaitility")
```

### 3.8 application.yml 補

```yaml
aethercare:
  kafka:
    topics:
      event-created: care.event.created
      workflow-started: care.workflow.started
      task-created: care.task.created
      notification-sent: care.notification.sent
      action-received: care.action.received
      audit-created: care.audit.created
  scheduler:
    timeout-scan-fixed-delay: 5000
  redis:
    workflow-lock-ttl-seconds: 300
    elder-status-ttl-seconds: 3600
```

---

## 4. 整合測試場景

`FallDetectedEndToEndIT`：
1. POST /api/v1/care-events {FALL_DETECTED} → 201 + workflowId
2. GET workflow → ACTIVE/WAITING_RESPONSE, level=1
3. Awaitility 等 ≤8s → 出現 level=2 task（壓縮 SLA seed 為 2 / 5）
4. POST /care-tasks/{l2}/actions {CONFIRM_SAFE} → 201
5. GET workflow → RESOLVED
6. GET audit-logs → 含完整序列：EVENT_CREATED → WORKFLOW_STARTED → TASK_CREATED(L1) → NOTIFICATION_SENT(L1) → TASK_TIMEOUT(L1) → TASK_ESCALATED → TASK_CREATED(L2) → NOTIFICATION_SENT(L2) → TASK_COMPLETED(L2) → WORKFLOW_RESOLVED
7. Kafka assertion：6 topics 各 ≥1 訊息

Timeout 壓縮：JdbcTemplate 改 sla_seconds → 2 / 5；scheduler fixed-delay → 500ms；用 Awaitility。

---

## 5. 驗收 Checklist

### 文件 18.1 功能驗收
1. POST /care-events 回 201 ✓
2. workflow instance 建立 ✓
3. level 1 task 建立 ✓
4. notification stub log ✓
5. 30s 後升級 level 2 ✓
6. CONFIRM_SAFE 後 RESOLVED ✓
7. audit timeline ≥10 筆完整序列 ✓

### 文件 18.2 技術驗收
1. 所有寫入 service @Transactional ✓
2. timeout scanner 對 markTimeoutIfPending 回 0 跳過 ✓
3. 連續 CONFIRM_SAFE：第二次 409 ✓
4. 6 個 service unit test ✓
5. Audit enum 全列舉值至少各 1 筆 ✓

### Spec 驗收
1. docker compose 三服務 healthy ✓
2. ./gradlew clean build ✓
3. ./gradlew test 含 IT ✓
4. ./gradlew bootRun + /actuator/health UP ✓
5. curl 跑通 18.1 ✓

---

## 6. Commit 切點（zh-TW）

| # | message | phase |
|---|---|---|
| 1 | chore: 加入基礎設施與 docker-compose 骨架 | 前置 |
| 2 | feat(db): 建立 MVP Liquibase changesets 與 demo seed | Phase 1 |
| 3 | feat(common): 加入 enum、共用設定、錯誤處理與時鐘抽象 | Phase 1 |
| 4 | feat(domain): 加入 entity、repository 與 conditional update queries | Phase 1 |
| 5 | feat(workflow): 實作 decision、workflow、task、notification、audit core service | Phase 1 |
| 6 | feat(api): 加入 REST controller、DTO 與全域例外處理 | Phase 2 |
| 7 | feat(scheduler): 加入 timeout scanner 與 escalation pipeline | Phase 3 |
| 8 | test: 加入 service unit tests 與 Testcontainers 端到端整合測試 | Phase 4+5 |
| 9 | docs: 補上 README demo 流程與 curl 範例 | 收尾 |

---

## 7. Executor 規約

- 不要 catch RuntimeException 阻斷 transaction rollback
- Entity 不用 @Data；用 @Getter @Setter + 自訂 equals based on id
- Timeout scanner 不開外層 transaction，逐一委派
- CareAuditService.log 用 REQUIRES_NEW，try/catch 只 log
- Redis lock 是 fast-fail 短路；DB conditional update 才是最終正確性防線
- Unit test 不啟 Spring context，只用 Mockito
- Controller 用 ResponseEntity<T>
- 所有 OffsetDateTime 從 common/time/Clock 拿

---

## 8. Critic 修訂（Must-fix）

### 8.1 Kafka publish 必須在 transaction commit 後

**禁止**在 `@Transactional` method 內直接呼叫 `kafkaTemplate.send(...)`。否則 DB rollback 時 Kafka 訊息已送出，違反責任鏈一致性。

**統一做法**：
1. service 內用 `ApplicationEventPublisher.publishEvent(domainEvent)` 發出本機 Spring 事件（含 topic name、key、payload record）
2. 在 `notification.KafkaPublishingListener`（或 `common.kafka.KafkaPublishingListener`）上掛 `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`，於 commit 後才呼叫 `kafkaTemplate.send`
3. 若 outer 沒有 transaction（罕見）：listener 也提供 `@EventListener` fallback 直接送（記 warning）

```java
// common/event/PublishToKafka.java
public record PublishToKafka(String topic, String key, Object payload) {}

// common/kafka/KafkaPublishingListener.java
@Component @RequiredArgsConstructor @Slf4j
public class KafkaPublishingListener {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommit(PublishToKafka ev) throws Exception {
        kafkaTemplate.send(ev.topic(), ev.key(), objectMapper.writeValueAsString(ev.payload()));
    }
    @EventListener
    public void onNoTx(PublishToKafka ev) throws Exception {
        if (TransactionSynchronizationManager.isActualTransactionActive()) return;
        kafkaTemplate.send(ev.topic(), ev.key(), objectMapper.writeValueAsString(ev.payload()));
    }
}
```

### 8.2 JPQL 不要手動 `version = version + 1`

`@Version` 欄位 Hibernate 會自動管，JPQL 內手動加 1 在 bulk update 會擲錯或被忽略。**改用 native SQL** 並標 `nativeQuery=true`：

```java
@Modifying
@Query(value = """
    UPDATE care_task
       SET status = 'TIMEOUT', updated_at = :now, version = version + 1
     WHERE id = :id
       AND status = 'PENDING'
       AND deadline_at < :now
    """, nativeQuery = true)
int markTimeoutIfPending(@Param("id") Long id, @Param("now") OffsetDateTime now);

@Modifying
@Query(value = """
    UPDATE care_task
       SET status = 'COMPLETED', completed_at = :now, updated_at = :now, version = version + 1
     WHERE id = :id
       AND status IN ('PENDING','ACKNOWLEDGED')
    """, nativeQuery = true)
int completeIfActive(@Param("id") Long id, @Param("now") OffsetDateTime now);

@Modifying
@Query(value = """
    UPDATE care_task
       SET status = 'ACKNOWLEDGED', acknowledged_at = :now, updated_at = :now, version = version + 1
     WHERE id = :id
       AND status = 'PENDING'
    """, nativeQuery = true)
int acknowledgeIfPending(@Param("id") Long id, @Param("now") OffsetDateTime now);
```

CareWorkflowInstanceRepository 同理改 native SQL。

`@Modifying` 加上 `clearAutomatically = true, flushAutomatically = true` 確保 persistence context 同步。

### 8.3 Timeout scanner 必須委派到 @Transactional method

```java
@Component
@RequiredArgsConstructor
public class CareTaskTimeoutScanner {
    private final CareTaskRepository taskRepo;
    private final CareTaskTimeoutHandler handler;   // 注意是另一個 bean
    private final Clock clock;

    @Scheduled(fixedDelayString = "${aethercare.scheduler.timeout-scan-fixed-delay}")
    public void scan() {
        for (CareTask t : taskRepo.findExpiredPendingTasks(clock.now())) {
            try { handler.handleTimeout(t.getId()); }
            catch (Exception e) { log.warn("timeout 處理失敗 taskId={}", t.getId(), e); }
        }
    }
}

@Component
@RequiredArgsConstructor
public class CareTaskTimeoutHandler {
    @Transactional
    public void handleTimeout(Long taskId) { /* markTimeoutIfPending → escalate */ }
}
```

self-invocation 會繞過 Spring proxy，導致 `@Transactional` 失效；**必須**呼叫到另一個 bean。

### 8.4 Nice-to-have（採納）

- 整合測試 Awaitility timeout = 15s；scheduler delay = 200ms（test profile）
- Lombok 顯式 `extra["lombok.version"] = "1.18.36"` 已套用 build.gradle.kts
- CareAuditService 新增 Micrometer `Counter` 觀測 `audit.write.failures`（autopilot 範圍內可不接 Prometheus，僅暴露 counter bean）
- Commit #5 拆分為：
  - 5a：feat(workflow): 加入 audit、notification stub 與 escalation contact service
  - 5b：feat(workflow): 加入 decision、task、workflow、event、action 與 timeout scanner

