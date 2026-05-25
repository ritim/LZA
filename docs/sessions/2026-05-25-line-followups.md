# Session Log — 2026-05-25：LINE 推播鏈路 follow-up 4 連發

> 用途：保留這個 Claude Code session 的工作脈絡。IDE 重開後可叫 Claude 讀這份檔案 resume context。
> 涵蓋日期：2026-05-25（單日，承接 [2026-05-24-line-push-pipeline.md](2026-05-24-line-push-pipeline.md) 收尾清單）。

---

## 主題摘要

把昨天 LINE 推播鏈路 session 留下的 4 個 follow-up 全部清掉，並且每個都有單元測試或端對端 IT 覆蓋。今日結束狀態：LINE 推播鏈路從「demo OK」進化到「multi-caregiver + 自動 refresh + dashboard 顯示綁定 + LINE 上能真正 ACK」的可用度。

---

## 任務清單

| # | 任務 | 狀態 |
|---|---|---|
| 1 | LINE postback「我收到了」實接 `CareActionService.acknowledge` | ✅ |
| 2 | LINE Flex 訊息加「📞 打給長輩」uri button | ✅ |
| 3 | Dashboard active event 卡片顯示 binding displayName | ✅ |
| 4 | 建第二個 caregiver 帳號 + 多家屬路由驗收（IT + playbook） | ✅ |
| 5 | LINE displayName 定期 refresh scheduler | ✅ |

> Anthropic API key rotate（昨天列為「明天必做」）今日**未做** — 改用 IDE 編輯 secrets.yml；不影響 LINE 鏈，下次仍要做。

---

## 主要技術決策

### #1 postback 真接 ACKNOWLEDGE

- `LineWebhookController.handlePostback` 從「只 reply」改為調用 `CareActionService.handle(taskId, caregiverId, ACKNOWLEDGE)`
- caregiverId 用 `lineBindingRepo.findByLineUserId(userId)` 反查
- **TenantContext 切換**：webhook 是 permitAll，無 JWT 進來，所以從 binding 拿 `tenantId` 手動 `TenantContext.set(...)` → `finally clear` — 確保 Hibernate `tenantFilter` 套用到正確 tenant
- 錯誤分流（每個都對應一句使用者看得懂的中文）：
  - `TASK_ALREADY_FINALIZED` → 「ℹ️ 此任務已被處理」
  - `NOT_FOUND` → 「❌ 找不到任務」
  - 其他 → 「⚠️ 系統暫時無法處理」
  - 未綁定 → 「⚠️ 您的 LINE 尚未綁定任何照護者帳號」
  - taskId 非數字 → 「❌ 無法解析任務編號」
- spec § Gap D：ACKNOWLEDGE **不結案 workflow**（dashboard 仍需 caregiver 做 CONFIRM_SAFE / ESCALATE）

### #2 「打給長輩」button

- `elder_profile` 加 `phone VARCHAR(50)` nullable（Liquibase 0024）— 同 changeset update demo 王美玉 301 / 1001 各補一個 demo 號碼
- 跟 `elder_contact.phone`（家屬電話）語意不同 — entity Javadoc 註記提醒
- Flex bubble footer 改成兩顆 button：
  - 上：橘色 `📞 打給{elderName}` — `action.type=uri` / `action.uri="tel:+886..."`，phone 經 `replaceAll("\\s+", "")` 去空白
  - 下：綠色「我收到了」postback（既有）
- 沒 phone → 只渲染 ACK button（不假裝）
- `NotificationService` 注入 `ElderProfileRepository`，在 `pushTaskToLine` 從 `CareEvent.elderId` 反查 elder

### #3 dashboard 顯示 binding displayName

- `DashboardResponse.ActiveEventItem` 加 `AssigneeRef { id, displayName, lineDisplayName, lineBound }`
- `DashboardService` 注入 `AppUserRepository` + `CaregiverLineBindingRepository`
- **批次撈** assignee + binding，避免 N+1（既有 elder profile / event 已是這個 pattern）
- 前端 `ActiveEventCard.vue`：
  - 綠色 chip `📱 LINE：{lineDisplayName}` — 綁定時
  - 灰色 chip `⚠️ 未綁 LINE` — 未綁時
- `lineBound` 是顯式 boolean，前端不要從 displayName==null 推斷（lineDisplayName 可能本來就是 null）

### #4 多家屬路由 + 驗收

**重要發現**：基礎設施其實**已就緒**，這次只是補洞和驗證。
- `family01` (id=2) / `family02` (id=3) 已由 `DemoUserSeeder` 內建 — 沒新增帳號
- `elder_id=1001` 既有完整 escalation 鏈（0007 seed）
- 補洞：`elder_id=301`（真實王美玉）原本沒 escalation；新增 changeset 0025 補 level 1 → family01 / level 2 → family02（兩層皆 LINE channel，讓 LINE 鏈路在 301 也通）
- **踩坑**：0025 一開始誤加 `tenant_id` 欄位，但 `care_contact_escalation` 表沒這個欄位（0015 / 0016 add-tenant-id 沒涵蓋到它）→ Liquibase 失敗→ 移除

**端對端 IT** `MultiCaregiverEscalationEndToEndIT`：
- 用 Testcontainers 起 PG / Redis / Kafka
- Test 1：SOS → level 1 task → UPDATE deadline=past → scanner 升級 → 驗 level 2 task assigneeId=3
- Test 2：兩層都 timeout → 驗 workflow 變 UNRESOLVED
- **重要發現**：scanner 升級的 audit action 名稱是 `TASK_ESCALATED`，**不是** `ESCALATION_TRIGGERED`
  - `TASK_ESCALATED` = `WorkflowEngine` 處理 SLA timeout
  - `ESCALATION_TRIGGERED` = `CareActionService` 處理 caregiver 主動 NEED_HELP
- 兩條路徑寫不同 audit，未來 grouping by escalation reason 會更清楚

**手動驗收 playbook**：`docs/multi-caregiver-routing-playbook.md`
- 5 個場景 + curl/SQL 指令 + audit 期望 + 清場 SQL
- 需要兩支手機（或一個人 + 同事）

### #5 displayName 自動 refresh

- `LineBindingRefreshScheduler`：每日 04:15 UTC（錯開既有 03:15 outbox / 03:30 refresh token cleanup）
- 流程：findAll → 對每個 binding 呼叫 `LineMessagingClient.fetchDisplayName` → 比對 → 不同才 save
- **失敗只 log 不 unbind** — 避免一次 API 異常清光所有綁定
- 三顆 counter（Prometheus）：`...refresh.updated` / `unchanged` / `failed`
- `@ConditionalOnProperty aethercare.line.binding.refresh.enabled` 預設 true
- LINE userId 在 log 用「前 6 後 2 + 中間 ***」遮罩，避免 PII

不選的設計：
- 不在 push 之前順手 refresh — Profile API 跟 push 共用 rate limit bucket
- 不 throttle — LINE Profile API rate limit 2000 req/min，MVP 規模遠不夠
- 不自動 unbind 失敗的 binding — 暫時 API 失敗 / user 換手機，不該無預警清掉

---

## 新增 / 修改的程式檔（速查）

### Backend Java

```
aethercare-api/src/main/java/com/lza/aethercare/
├── dashboard/
│   ├── dto/DashboardResponse.java                              (加 AssigneeRef)
│   └── service/DashboardService.java                           (注入 AppUserRepo + LineBindingRepo)
├── notification/
│   ├── line/
│   │   ├── LineWebhookController.java                          (改：postback 實接 ACKNOWLEDGE)
│   │   └── scheduler/
│   │       └── LineBindingRefreshScheduler.java                (新：daily 04:15 refresh)
│   └── service/NotificationService.java                        (加 ElderProfileRepo + tel button)
└── userprofile/entity/ElderProfile.java                        (加 phone 欄位)
```

### Backend Tests

```
aethercare-api/src/test/java/com/lza/aethercare/
├── dashboard/DashboardServiceTest.java                         (新，3 tests)
├── integration/MultiCaregiverEscalationEndToEndIT.java         (新，2 IT — Testcontainers)
├── notification/
│   ├── NotificationServiceTest.java                            (補 mock + 加 2 tests)
│   └── line/
│       ├── LineBindingRefreshSchedulerTest.java                (新，5 tests)
│       └── LineWebhookControllerTest.java                      (新，4 tests)
├── recipient/RecipientSelfControllerTest.java                  (補 RecipientNotificationService mock)
└── userprofile/CareRecipientControllerTest.java                (補 CheckInHistoryService mock)
```

### Backend Resources

```
src/main/resources/db/changelog/changes/
├── 0024-add-elder-profile-phone.yaml                           (新)
└── 0025-seed-elder-301-escalation.yaml                         (新)
```

### Frontend Vue

```
aethercare-web/src/
├── api/types.ts                                                (加 DashboardAssigneeRef)
└── components/ActiveEventCard.vue                              (加派發給 / LINE chip)
```

### Docs

```
docs/
└── multi-caregiver-routing-playbook.md                         (新，手動驗收 playbook)
```

---

## 已知小坑（今日新增的）

- **`care_contact_escalation` 沒 tenant_id 欄位** — 0015 / 0016 加 tenant_id 漏了它。新增 seed / IT 都要記得。可能未來需要 0026-add-tenant-id-to-escalation.yaml。
- **Audit action 名稱兩派** — 同樣是「task 升級到下一 level」：
  - scanner 自動升級 → `TASK_ESCALATED`
  - caregiver 主動 ESCALATE / NEED_HELP → `ESCALATION_TRIGGERED`
  - 寫 IT / dashboard timeline 過濾條件時記得兩個都要列。
- **NotificationServiceTest 過去隱性錯**：@InjectMocks 沒涵蓋 lineClientProvider / lineBindingRepo / careEventRepo（昨天加的 dep），跑到 pushTaskToLine 會 NPE。本 session 補齊。
- **Bash hook 強制 parallel / 偶發 swallow output**：`git status` 在 cwd=aethercare-api 下 output 被 rtk 吃掉，用 `/usr/bin/git -C <path> ... > /tmp/x.txt; cat` 才看到。實際 working tree 正常。
- **commit 不是 Claude 做的**：今天的工作被外部 mechanism（IDE hook？）自動 commit 為 `feat(init): init`（commit `63d27de`）。Session 結束時 working tree clean。下次注意 commit message 風格不一致。
- **secrets.yml 真的不要再貼**：昨天提到 Anthropic key leaked，今天仍未 rotate；繼續用 IDE 編輯方式。

---

## 明天接續清單

### 必做

1. **Anthropic API key rotate**（從昨天積到今天）
   - console.anthropic.com → 撤舊建新
   - IDE 編輯 `aethercare-api/src/main/resources/secrets.yml`
   - 順手 Plans & Billing 儲值 $5
   - 重啟 backend

### 推薦 follow-up

- **0026-add-tenant-id-to-escalation**：補上 `care_contact_escalation.tenant_id`，跟其他表一致（avoid future cross-tenant 看到別人 escalation）
- **LINE Flex 「目前等候的家屬」標示**：當 task 升級時，前一個 caregiver 收一封通知說「已升級到 XXX」— 透明度比較好
- **Dashboard SLA 倒數變色**：低於某秒數變紅，呼應 LINE Flex 已經做的「剩約 X 秒」
- **第二輪測試**：用兩支手機 / 兩個 LINE 帳號照 playbook 跑一遍場景 1~5，把 LINE 端的截圖留下來

### 永久議題（昨天就有）

- `secrets.yml` 機制不適合 production，要改 Vault / k8s secret / AWS Secrets Manager
- cloudflared dev tunnel 要付費 plan 才有 SLA
- LINE Profile API 有 rate limit（2000 req/min），目前 refresh scheduler 不分頁，binding 數大時要加 batchSize / throttle

---

## 服務啟動順序（dev）

跟昨天一致，沒有變動：

```bash
# 1. Docker 依賴
docker compose up -d

# 2. Backend
cd /Users/yao/0.Projects/LZA/aethercare-api && sh ./gradlew bootRun

# 3. Frontend
cd /Users/yao/0.Projects/LZA/aethercare-web && npm run dev

# 4. Cloudflared 固定 tunnel
cloudflared tunnel --config ~/.cloudflared/aethercare-dev.yml run
```

驗證 multi-caregiver 鏈：依 `docs/multi-caregiver-routing-playbook.md` 跑場景 1~5。
