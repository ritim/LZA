/**
 * Multi-tenant SaaS framework：tenant 表 + TenantContext + Hibernate @Filter aspect。
 *
 * <p>本 package 對應系統設計 §20 Phase 2 multi-tenant framework。MVP 採 shared schema +
 * tenant_id column 模式，搭配 Hibernate {@code @Filter} 在 query 時自動加 WHERE tenant_id=?，
 * 達到 row-level 隔離。詳見 {@code docs/deployment/multi-tenant-roadmap.md}。
 *
 * <p>{@link org.hibernate.annotations.FilterDef} 為全域唯一定義；具體 entity 用
 * {@link org.hibernate.annotations.Filter} 引用 {@code tenantFilter}（CareEvent /
 * CareWorkflowInstance / CareTask）。
 */
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
package com.lza.aethercare.tenant;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
