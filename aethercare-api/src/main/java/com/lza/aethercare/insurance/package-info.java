/**
 * Insurance integration：對保險公司開放的「照護證據」查詢端點，回傳 elder 在
 * 指定區間內的事件 / workflow / 責任鏈摘要，用於保費評估與理賠佐證。
 *
 * <p>受 INSURANCE role 保護（見 {@code SecurityConfig}）。所有查詢都會寫一筆
 * {@code CareAuditAction.INSURANCE_QUERY} audit log（workflowId=null，由
 * 0013 migration 改為 nullable 後支援）。
 *
 * <p>認證演進路徑、欄位隱私權衡、rate limit 建議與整合範例請見
 * {@code docs/deployment/insurance-integration-runbook.md}。
 */
package com.lza.aethercare.insurance;
