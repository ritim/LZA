/**
 * Assessment 模組：caregiver 提交評估問卷答案，service 套用 knowledge.dangerAnswer 規則
 * 進行危險偵測，並產生 risk reevaluation 訊息給前端。
 *
 * <p>對應 spec §5.5 / §10.4 「危險動作偵測」流程。資料表 {@code care_assessment_answer}
 * 由 Liquibase 0017 建立。
 */
package com.lza.aethercare.assessment;
