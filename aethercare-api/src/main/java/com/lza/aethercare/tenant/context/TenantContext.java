package com.lza.aethercare.tenant.context;

/**
 * 當前 request 的 tenant id：由 {@code JwtAuthenticationFilter} 從 JWT claim 設置，
 * service 層透過此取值；filter 末段必須 {@link #clear()} 避免 thread pool 殘留。
 *
 * <p>沒有 token 時（system thread / scheduler / IT 未登入路徑）回 default tenant id=1，
 * 確保既有資料相容。
 */
public final class TenantContext {

    /** 系統預設 tenant id（對應 0014 seed 'default'）。 */
    public static final Long DEFAULT_TENANT_ID = 1L;

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Long tenantId) {
        CURRENT.set(tenantId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static Long getOrDefault() {
        Long v = CURRENT.get();
        return v != null ? v : DEFAULT_TENANT_ID;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
