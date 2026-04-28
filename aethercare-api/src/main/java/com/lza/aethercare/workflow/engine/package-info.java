/**
 * Workflow engine 抽象層：定義 {@link com.lza.aethercare.workflow.engine.WorkflowEngine}
 * 介面，為未來 Temporal / Cadence 等 durable workflow runtime 預留切換點。
 *
 * <p>MVP 實作仍位於 {@code com.lza.aethercare.workflow.service.CareWorkflowService}
 * （@Scheduled scanner + Postgres conditional update + Redis 互斥鎖），
 * Phase 2.x 加入 {@code TemporalWorkflowEngineImpl} 後由 profile 切換 bean。
 *
 * <p>Migration 細節請參考
 * {@code docs/deployment/temporal-migration-roadmap.md}。
 */
package com.lza.aethercare.workflow.engine;
