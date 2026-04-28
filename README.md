# LZA Monorepo — AetherCare AI

柳智有限公司 / AetherCare AI 的單一 repo。

## 子專案

| 路徑 | 內容 |
|---|---|
| [`aethercare-api/`](aethercare-api/) | Home Care Copilot 後端（Spring Boot 3.5 / Java 21） |
| [`docs/`](docs/) | 系統設計、Codex prompt、規劃文件 |

## 主要設計文件

- 系統設計：[`docs/system_design/aethercare_codex_system_design.md`](docs/system_design/aethercare_codex_system_design.md)

## 本機開發環境

啟動 PostgreSQL / Redis / Kafka：

```bash
docker compose up -d
docker compose ps
```

啟動後端：

```bash
cd aethercare-api
./gradlew bootRun
```

詳見 [`aethercare-api/README.md`](aethercare-api/README.md)。
