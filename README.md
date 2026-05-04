# LZA / AetherCare Documentation Index

本資料夾目前是 LZA 與 AetherCare MVP 的規格工作區。

## 文件使用方式

請先閱讀新的 `docs/` 文件，舊文件保留為設計來源與歷史參考。

| 文件 | 用途 |
|---|---|
| `CLAUDE.md` | Claude Code 執行開發時的根指令。請先讀這份。 |
| `docs/AetherCare_MVP_Master_Spec.md` | AetherCare MVP 主規格。後續產品、前端、後端、AI、schema 實作以這份為準。 |
| `docs/AetherCare_AI_Care_Chat_Spec.md` | 事件綁定 AI 處置助手規格。用來實作照顧者端基礎 AI 對話。 |
| `docs/AetherCare_MVP_Gap_Review.md` | 重新檢視後的缺口、補足項目與已採用決策。 |
| `docs/AetherCare_MVP_Implementation_Roadmap.md` | 可交給工程實作的分階段 roadmap、測試情境與完成標準。 |
| `aethercare_codex_system_design.md` | 原始系統設計與 workflow engine 詳細草案。 |
| `aethercare_frontend_backend_api_integration.md` | 原始前後端 API、AI guidance、UX 規格草案。 |
| `AetherCare_PostgreSQL_Schema.sql` | 目前 PostgreSQL schema 草案。實作前請依 master spec 再做 migration 整理。 |
| `LZA.md` | LZA 品牌架構。 |
| `起草書.txt` | 柳智品牌敘事與創業原始宣言。 |

## Canonical Product Statement

> AetherCare 是一套讓被照顧者能被聽見、讓照顧者不再孤軍奮戰、讓每一次照護事件都被接住的 AI 居家照護系統。

## Canonical Terms

| 中文 | 英文 | 說明 |
|---|---|---|
| 被照顧者 | care recipient | 系統照護對象。避免在新文件中使用 elder 作為通用名稱。 |
| 照顧者 | caregiver | 家屬、看護、鄰居、機構人員等可接任務者。 |
| 原始訊號 | activity log / signal | check-in、SOS、手機最後活動、手動回報、無回應等原始資料。 |
| 照護事件 | care event | 需要追蹤或處理的事件，例如 SOS、未回報、無活動、疑似跌倒。 |
| 照護任務 | care task | 指派給某位照顧者、帶有 SLA 的任務。 |
| 責任鏈 | audit timeline | 事件、通知、確認、升級、處置的完整紀錄。 |

## Next Step

依序執行：

1. 讓 Claude Code 先讀 `CLAUDE.md`。
2. 用 `docs/AetherCare_MVP_Master_Spec.md` 鎖定 MVP 產品與系統範圍。
3. 用 `docs/AetherCare_MVP_Implementation_Roadmap.md` 建立實際前後端專案。
4. 用 `docs/AetherCare_MVP_Gap_Review.md` 檢查不要回到舊命名或舊範圍。
