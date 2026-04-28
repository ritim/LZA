# aethercare-web

AetherCare AI 照護 Dashboard（Vue 3 + TypeScript + Vite）。
搭配 [`aethercare-api/`](../aethercare-api/) 後端執行 demo flow。

## 啟動

先確保後端已啟動（PostgreSQL / Redis / Kafka 由 docker compose 啟動，aethercare-api 由 `./gradlew bootRun` 啟動，listen :8080）。

```bash
cd aethercare-web
npm install
npm run dev
```

預設打開 http://localhost:5173 。

## Demo 帳號

| username | password   | 角色 |
|----------|------------|------|
| family01 | family123  | 家屬 |
| admin    | admin123   | 管理員 |

## Demo 流程

1. 用 `family01 / family123` 登入。
2. 點「建立 FALL_DETECTED」按鈕：前端會 POST `/api/v1/care-events`（elderId=1001），後端啟動 workflow 並回傳 workflowId。
3. 自動跳到 `/workflows/:id`，每 2 秒輪詢一次 workflow 狀態 + audit log。
4. 預設 30 秒未確認 → 任務 TIMEOUT → 升級到下一層責任鏈。
5. 在 task card 上按 `CONFIRM_SAFE` / `NEED_HELP` / `ACKNOWLEDGE` 即可回填動作。

## 設定

- 後端 baseURL 寫死 `http://localhost:8080`（`src/api/client.ts`）。
- 後端 CORS allowed-origins 已包含 `http://localhost:5173`（`aethercare-api/src/main/resources/application.yml`）。
- JWT access / refresh token 自動寫入 `localStorage`，401 自動 refresh。
