package com.lza.aethercare.tenant.aspect;

import com.lza.aethercare.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * 對 event/workflow/task service 自動 enable Hibernate {@code tenantFilter}，
 * 讓所有 query 自動加 {@code WHERE tenant_id = :tenantId}，達到 row-level 隔離。
 *
 * <p>範圍刻意縮窄到三個 package，避免影響既有 service（auth / audit / userprofile…）。
 * 既有 service 不需 tenant 概念，{@code TenantContext} 為 null 時 aspect 直接 proceed。
 *
 * <p>注意：filter 啟用是 session-scoped；同 transaction 內多次 service 呼叫不會重複啟用。
 * 在 finally disableFilter 確保不污染 thread pool 中下個 request 共用的 session。
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class TenantFilterAspect {

    @PersistenceContext
    private EntityManager em;

    @Around("execution(* com.lza.aethercare.event.service..*(..))"
            + " || execution(* com.lza.aethercare.workflow.service..*(..))"
            + " || execution(* com.lza.aethercare.task.service..*(..))"
            + " || execution(* com.lza.aethercare.action.service..*(..))"
            + " || execution(* com.lza.aethercare.audit.service..*(..))"
            + " || execution(* com.lza.aethercare.anomaly.service..*(..))"
            + " || execution(* com.lza.aethercare.insurance.service..*(..))"
            + " || execution(* com.lza.aethercare.ai.service..*(..))"
            + " || execution(* com.lza.aethercare.assessment.service..*(..))")
    public Object enableTenantFilter(ProceedingJoinPoint pjp) throws Throwable {
        Long tenantId = TenantContext.get();
        if (tenantId == null) {
            // scheduler / unauthenticated 路徑：不 enable filter，沿用既有行為
            return pjp.proceed();
        }
        Session session = em.unwrap(Session.class);
        boolean enabledHere = false;
        if (session.getEnabledFilter("tenantFilter") == null) {
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            enabledHere = true;
        }
        try {
            return pjp.proceed();
        } finally {
            if (enabledHere) {
                session.disableFilter("tenantFilter");
            }
        }
    }
}
