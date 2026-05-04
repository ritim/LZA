/**
 * Spec § AI_Care_Chat：照顧者端事件綁定 AI Care Chat 模組。
 *
 * <p>核心原則：AI 引導行動、workflow 控制狀態。Chat 永遠綁在 careEventId / workflowId /
 * 當前 task；suggested actions 只是按鈕，需要 caregiver 經 workflow action API 才會改變 state。
 *
 * <p>MVP 不接 LLM，靠 deterministic rules + static guidance 提供 reply / questions /
 * suggestedActions / dangerSigns / disclaimer 結構化回應。
 */
package com.lza.aethercare.aichat;
