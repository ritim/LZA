/**
 * AI 照護輔助層：載入 care-knowledge 靜態 JSON 提供 caregiver 結構化指引，
 * 並預留 {@link com.lza.aethercare.ai.llm.LlmProvider} interface 給未來真實 LLM swap。
 *
 * <p>本 package 對應 spec §5.5 / §10.4 AI Care Guidance。MVP 採用
 * {@link com.lza.aethercare.ai.llm.StaticLlmProvider} 直接由 KnowledgeBase 提供內容；
 * production 可實作另一個 {@code LlmProvider} 並以 {@code aethercare.ai.provider}
 * 設定切換。
 */
package com.lza.aethercare.ai;
