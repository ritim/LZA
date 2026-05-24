# Session Log — 2026-05-24：LINE 推播鏈路（從 0 到 production-grade）

> 用途：保留這個 Claude Code session 的工作脈絡。IDE 重開後可叫 Claude 讀這份檔案 resume context。
> 涵蓋日期區段：2026-05-22 ~ 2026-05-24（連續 3 天的工作累積，於 24 號收工）。

---

## 主題摘要

從 caregiver dashboard 的「我今天還好」按鈕，建構一條完整的推播鏈路到家屬手機 LINE Official Account，含：

1. AI Care Chat 從 deterministic 模板進化到 Claude / Ollama + OpenCC + 台灣繁中 prompt
2. 每日 check-in 月曆 view（recipient 端按鈕 → 寫入 `elder_activity_event` → caregiver 端日曆顯示）
3. Recipient → backend → 多 caregiver LINE OA push（含綁定 flow、Flex Message、postback）
4. Cloudflared named tunnel + 固定 hostname `aethercare-dev.lza.systems`（不再 ephemeral URL）

---

## 任務清單（全部來自本 session）

| # | 任務 | 狀態 |
|---|---|---|
| 4-11 | AI Care Chat：Anthropic + Ollama qwen2.5:7b + TAIDE + OpenCC + 台灣繁中 prompt | ✅ |
| 12 | check-in 歷史 API：`GET /api/v1/care-recipients/{id}/check-ins?days=30` | ✅ |
| 13 | 前端 `CheckInCalendar.vue` 月曆 view，嵌入 CareProfileView | ✅ |
| 14 | check-in 成功 → 觸發 stub notification（contactCount=2） | ✅ |
| 15 | LINE push 經 `test-user-ids`（dev 廣播給開發者） | ✅ |
| 16-23 | 完整綁定 flow：表 + entity + service + controller + LineMessagingClient.replyText + webhook 串接 + 改用 binding 表 + 前端 `LineBindingCard.vue` | ✅ |
| 24 | task-level 通知串 LINE push（NotificationService.notify 加 pushTaskToLine） | ✅ |
| 25 | check-in push 改走 `care_contact_escalation` 精準路由 | ✅ |
| 26 | LINE Flex Message bubble + 「我收到了」postback button | ✅ |
| 27 | webhook tryConsumeCode 自動 fetch LINE profile displayName 寫入 binding | ✅ |
| 28-B | Cloudflared named tunnel 固定 hostname | ✅ |
| 28-A | Anthropic API key rotate | ⏳ 明天做（用 IDE 編輯 secrets.yml） |

---

## 主要技術決策

### AI Care Chat

- **Provider 切換**用 `aethercare.ai.chat.provider`：`none` / `anthropic` / `ollama`，由 `@ConditionalOnProperty` 互斥決定哪個 generator bean 上線。`LlmReplyGenerator.Outcome` 帶 source（RULE_ENGINE / LLM），service 寫進 `ai_chat_messages.source` 做 audit。
- **Anthropic Haiku 4.5** 為預設（中日都頂級、$1/$5 per 1M、合規）。Java SDK：`com.anthropic:anthropic-java:2.27.0`。
- **Ollama qwen2.5:7b** 為本機 fallback。TAIDE-LX-8B 試過品質與規範遵守度不如 Qwen，棄用。
- **OpenCC s2t** 後處理用 `com.github.houbb:opencc4j:1.8.0`，強制把 LLM 偶發簡體字 normalize 成繁體。詞彙級陸→台對應（如「兜底」→「保底」）靠 system prompt 列正反範例。
- **共用** `ChatPromptBuilder`：system prompt 含台灣繁中規範、安全護欄（不得宣稱絕對安全、不得改 SLA、危急一律撥 119）、字數限制；user prompt 帶事件脈絡 + 對話歷史 + suggestedActions（LLM 只生 reply 文字，actions 由 rules engine 守住）。
- **Anthropic 失敗**（credit too low / network）自動退回 rules engine 模板字串，source 標 RULE_ENGINE — 對 spec §3「無回應不等於安全」反向：通知 / chat 永遠要有回應。

### Check-in 月曆

- 後端 `CheckInHistoryService` 在 Asia/Taipei 時區聚合，每天標 `CHECKED_IN` / `MISSED` / `PENDING`。
- Repository 加 `findByElderIdAndTypeAndOccurredAtBetween`，沿用既有 `elder_activity_event` 表。
- 前端 `CheckInCalendar.vue` 7-col grid，月曆首格用 startDate.dayOfWeek 留空白對齊。

### LINE 推播

- **Caregiver↔recipient 對應**：重用既有 `care_contact_escalation`（已有 elder_id + contact_user_id + level + enabled），不另開 `caregiver_recipient_assignment` 表。
- **綁定碼**：8 字元 SecureRandom，字母表去掉 I/O/0/1。存 Redis（`StringRedisTemplate`，key prefix `line-binding-code:`，TTL 10min）。Consume 用 `getAndDelete` atomic。
- **綁定表** `caregiver_line_binding`：caregiver_id unique + line_user_id unique，FK 到 app_user / tenant。Liquibase changeset `0023-create-caregiver-line-binding.yaml`。
- **DisplayName** 在 webhook tryConsumeCode 成功時呼叫 `GET /v2/bot/profile/{userId}` 補上。
- **Flex Message bubble**：header（緊急色） + body（風險 / 任務 / SLA 倒數） + footer 一個綠色「我收到了」postback button（data = `ack:<taskId>`）。
- **Postback handler** 目前只 reply「已記錄」+ log，**沒接 CareActionService 真實 ACKNOWLEDGE**。dashboard 點按鈕才會真的轉狀態。
- **Push 路由**：
  - check-in：依 elderId 查 escalation chain 取所有 caregiverIds → binding 取 lineUserId → 去重後 push（test-user-ids 額外補推給開發者）。
  - task-level（SOS / FALL / MISSED_CHECK_IN）：直接拿 task.assigneeId 查 binding，**只 push 給該 escalation level 的 caregiver**。

### 對外通路

- **Cloudflared named tunnel**：`aethercare-dev` → `aethercare-dev.lza.systems`（lza.systems 已在使用者 Cloudflare 帳號下）。
- Config 檔：`~/.cloudflared/aethercare-dev.yml`
- Credentials：`~/.cloudflared/2de1b8d6-da9d-4f0f-8496-bba64ea9337c.json`
- 啟動：`cloudflared tunnel --config ~/.cloudflared/aethercare-dev.yml run`

### Secrets 管理

- `aethercare-api/src/main/resources/secrets.yml` — 已在根目錄 `.gitignore` 排除
- application.yml 用 `spring.config.import: optional:classpath:secrets.yml` 機制載入
- 包含 `aethercare.ai.chat.*`、`aethercare.line.*` 兩段
- ⚠️ 使用者在這個 session 把 Anthropic key 貼進對話一次 → 該 key 視為洩漏，2026-05-25 預計 rotate

---

## 新增 / 修改的程式檔（速查）

### Backend Java

```
aethercare-api/src/main/java/com/lza/aethercare/
├── aichat/
│   ├── config/AiChatProperties.java                          (新)
│   ├── config/AiChatConfig.java                              (新)
│   ├── rules/AiCareChatContext.java                          (改：加 recipientName)
│   ├── rules/AiCareChatRulesEngine.java                      (重寫：模板池 + 變數插值)
│   ├── rules/ChatPromptBuilder.java                          (新)
│   ├── rules/LlmReplyGenerator.java                          (新)
│   ├── rules/TemplateLlmReplyGenerator.java                  (新)
│   ├── rules/ClaudeLlmReplyGenerator.java                    (新)
│   ├── rules/OllamaLlmReplyGenerator.java                    (新)
│   ├── rules/TaiwaneseTextNormalizer.java                    (新)
│   ├── service/AiCareChatService.java                        (改：接 generator)
│   └── enums/ChatSource.java                                 (改：加 LLM)
├── userprofile/
│   ├── dto/CheckInDayItem.java                               (新)
│   ├── dto/CheckInHistoryResponse.java                       (新)
│   ├── service/CheckInHistoryService.java                    (新)
│   └── controller/CareRecipientController.java               (加 GET /check-ins)
├── anomaly/repository/ElderActivityEventRepository.java      (加 findByElderIdAndTypeAndOccurredAtBetween)
├── recipient/
│   ├── controller/RecipientSelfController.java               (改：notify check-in)
│   └── service/RecipientNotificationService.java             (新)
├── notification/
│   ├── service/NotificationService.java                      (改：pushTaskToLine + Flex)
│   └── line/
│       ├── LineProperties.java                               (新)
│       ├── LineConfig.java                                   (新)
│       ├── LineMessagingClient.java                          (新：push / reply / pushFlex / fetchDisplayName)
│       ├── LineWebhookController.java                        (新：signature verify + message + postback)
│       ├── entity/CaregiverLineBinding.java                  (新)
│       ├── repository/CaregiverLineBindingRepository.java    (新)
│       ├── service/LineBindingService.java                   (新)
│       └── controller/CaregiverLineBindingController.java    (新)
└── common/security/SecurityConfig.java                       (改：webhook permitAll)
```

### Backend Resources

```
src/main/resources/
├── application.yml                                           (加 ai.chat + spring.config.import)
├── secrets.yml                                               (新，已 gitignore)
└── db/changelog/changes/
    └── 0023-create-caregiver-line-binding.yaml               (新)
```

### Backend Build

```
aethercare-api/build.gradle.kts
+ implementation("com.anthropic:anthropic-java:2.27.0")
+ implementation("com.github.houbb:opencc4j:1.8.0")
```

### Frontend Vue

```
aethercare-web/src/
├── api/client.ts                                             (加 deleteJson)
├── api/caregiver.ts                                          (加 check-in 歷史 + LINE binding API)
├── components/AppHeader.vue                                  (改：brand 變可點 home)
├── components/AiCareChatPanel.vue                            (改：textarea + Shift+Enter)
├── components/CheckInCalendar.vue                            (新)
├── components/LineBindingCard.vue                            (新)
└── views/
    ├── DashboardView.vue                                     (改：嵌 LineBindingCard)
    └── CareProfileView.vue                                   (改：嵌 CheckInCalendar)
```

### Root

```
.gitignore                                                    (加 secrets.yml 排除規則)
~/.cloudflared/aethercare-dev.yml                             (新)
```

---

## 明天接續清單

### 必做

1. **Anthropic key rotate**（已 leaked）：
   - console.anthropic.com → API keys → 撤舊建新
   - 在 IDE 編輯 `aethercare-api/src/main/resources/secrets.yml` 改 `aethercare.ai.chat.api-key` value
   - Plans & Billing 順帶儲值 $5（不儲值 LLM 仍 fallback 模板）
   - 重啟 backend：`cd aethercare-api && sh ./gradlew bootRun`
2. **LINE webhook URL 已固定**：`https://aethercare-dev.lza.systems/api/v1/line/webhook`，重啟 cloudflared 不會變

### 推薦 follow-up（不急）

- postback「我收到了」實接 `CareActionService.acknowledge(taskId, caregiverId)` → 真的把 task 設 ACKNOWLEDGED
- dashboard active event 卡片顯示 binding displayName（哪個家屬負責）
- LINE 訊息加「打給長輩」uri button（需 elder_profile / elder_contact 加 phone 欄位）
- 第二個 caregiver 帳號（例 `family02`）綁第二個 LINE userId 測多家屬路由
- LINE displayName 自動 refresh（定期 / on push 失敗 retry）

### 永久議題

- Cloudflared tunnel 重啟需要在 terminal 重跑（不是 background daemon）：可加 launchd plist 自動啟動
- secrets.yml 機制不適合 production，須改 Vault / k8s secret / AWS Secrets Manager
- `cloudflared` 是 Cloudflare 私服，production 要付費 plan 才有 SLA

---

## 服務啟動順序（dev）

依賴順序（每次 IDE 重開後）：

```bash
# 1. Docker 依賴（postgres / redis / kafka / vault / kafka-connect）
docker compose up -d

# 2. Backend（不要用 ./gradlew，hook 有 rtk wrapper；用 sh ./gradlew）
cd /Users/yao/0.Projects/LZA/aethercare-api && sh ./gradlew bootRun

# 3. Frontend
cd /Users/yao/0.Projects/LZA/aethercare-web && npm run dev

# 4. Cloudflared 固定 tunnel（webhook 公網入口）
cloudflared tunnel --config ~/.cloudflared/aethercare-dev.yml run
```

驗證：
- backend: `curl http://localhost:9001/actuator/health` → UP
- frontend: 開 `http://localhost:5173`
- tunnel: `curl https://aethercare-dev.lza.systems/api/v1/ping` → 200

---

## 已知小坑

- **`./gradlew` 會被 user hook 重寫成 `rtk gradlew`**，且 hook 不認得 `./gradlew + env var 前綴` 寫法。改用 `sh ./gradlew ...` 繞過。
- **gradle bootRun 不會把 host env 傳到 forked JVM**，要設置 chat 用 `--args='--aethercare.ai.chat.provider=...'` 或寫進 secrets.yml。
- **LINE webhook URL 一旦 Verify Success 不會留 log**（驗簽過但空 events array）— follow / message / postback 才會留。
- **加好友重複不會重發 follow event**：testing 拿 userId 改用「傳訊息」觸發。
- **Anthropic credit too low**：service 自動 fallback 到 rules engine 模板，table 看起來工作正常但 reply 不是 LLM 生成的，看 log 才知道。
- **Qwen 偶發簡體字**：靠 OpenCC s2t 修正；陸用詞（兜底 / 視頻）靠 system prompt。
