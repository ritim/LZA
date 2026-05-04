/**
 * Recipient self-service 模組：spec §0 / §7 規定 {@code /api/v1/recipient/*} 為被照顧者
 * 自助動作 namespace。本 package 不重複實作底層 service，僅 wire 到既有 ActivityIngestion /
 * CareEvent 服務，把訊號變成 activity log 或 care event。
 *
 * <p>Auth：MVP 採 {@code X-Care-Recipient-Id} header 模擬身份；SecurityConfig 已將
 * {@code /api/v1/recipient/**} 列為 permitAll。production 必須換成真認證。
 */
package com.lza.aethercare.recipient;
