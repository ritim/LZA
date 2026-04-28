package com.lza.aethercare.workflow.service;

import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.workflow.dto.SlaSummaryResponse;
import com.lza.aethercare.workflow.dto.SlaTimelineBucket;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * SLA metrics 服務：以 native SQL 對 care_workflow_instance / care_audit_log /
 * care_task 做彙總，回傳 dashboard / 保險證明所需的指標。
 *
 * <p>所有 query 都加 [from, to) 視窗條件，避免全表掃描；timeline 透過
 * {@code DATE_TRUNC} 切 bucket。
 */
@Service
@Slf4j
public class SlaMetricsService {

    /** 允許的 bucket 粒度，限制只能傳這兩個避免 SQL injection。 */
    private static final List<String> ALLOWED_BUCKETS = List.of("hour", "day");

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public SlaSummaryResponse summary(OffsetDateTime from, OffsetDateTime to) {
        validateRange(from, to);

        Object[] wfRow = (Object[]) em.createNativeQuery("""
                SELECT
                  COUNT(DISTINCT w.id)                                                        AS total_workflows,
                  COUNT(DISTINCT CASE WHEN w.status='RESOLVED'   THEN w.id END)               AS resolved,
                  COUNT(DISTINCT CASE WHEN w.status='UNRESOLVED' THEN w.id END)               AS unresolved,
                  AVG(EXTRACT(EPOCH FROM (w.completed_at - w.started_at)))                    AS avg_resolve_seconds
                FROM care_workflow_instance w
                WHERE w.started_at >= :from AND w.started_at < :to
                """)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();

        long total = toLong(wfRow[0]);
        long resolved = toLong(wfRow[1]);
        long unresolved = toLong(wfRow[2]);
        Double avgResolveSeconds = toDoubleOrNull(wfRow[3]);

        Object[] escRow = (Object[]) em.createNativeQuery("""
                SELECT
                  COUNT(DISTINCT w.id)                                                        AS total,
                  COUNT(DISTINCT CASE WHEN a.action='TASK_ESCALATED' THEN w.id END)           AS escalated
                FROM care_workflow_instance w
                LEFT JOIN care_audit_log a ON a.workflow_id = w.id
                WHERE w.started_at >= :from AND w.started_at < :to
                """)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        long escalationDenominator = toLong(escRow[0]);
        long escalated = toLong(escRow[1]);

        Double avgFirstResponseSeconds = toDoubleOrNull(em.createNativeQuery("""
                SELECT AVG(EXTRACT(EPOCH FROM (COALESCE(t.acknowledged_at, t.completed_at) - t.created_at)))
                FROM care_task t
                WHERE t.created_at >= :from AND t.created_at < :to
                  AND (t.acknowledged_at IS NOT NULL OR t.completed_at IS NOT NULL)
                """)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult());

        double resolvedRate = total == 0 ? 0.0 : (double) resolved / total;
        double escalationRate = escalationDenominator == 0
                ? 0.0
                : (double) escalated / escalationDenominator;

        return new SlaSummaryResponse(
                from, to,
                total, resolved, unresolved,
                resolvedRate, escalationRate,
                avgFirstResponseSeconds, avgResolveSeconds);
    }

    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<SlaTimelineBucket> timeline(OffsetDateTime from, OffsetDateTime to, String bucket) {
        validateRange(from, to);
        String safeBucket = normalizeBucket(bucket);

        // DATE_TRUNC 第一個參數必須是 literal 而非 bind param，因此先白名單檢查再內插。
        String startedSql = """
                SELECT DATE_TRUNC('%s', w.started_at) AS bucket_start,
                       COUNT(*)                       AS workflows_started,
                       COUNT(CASE WHEN w.status='RESOLVED' THEN 1 END) AS workflows_resolved
                  FROM care_workflow_instance w
                 WHERE w.started_at >= :from AND w.started_at < :to
                 GROUP BY DATE_TRUNC('%s', w.started_at)
                 ORDER BY bucket_start
                """.formatted(safeBucket, safeBucket);

        List<Object[]> startedRows = em.createNativeQuery(startedSql)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        String escalationSql = """
                SELECT DATE_TRUNC('%s', a.created_at) AS bucket_start,
                       COUNT(DISTINCT a.workflow_id)  AS escalations
                  FROM care_audit_log a
                 WHERE a.created_at >= :from AND a.created_at < :to
                   AND a.action = 'TASK_ESCALATED'
                 GROUP BY DATE_TRUNC('%s', a.created_at)
                """.formatted(safeBucket, safeBucket);

        List<Object[]> escalationRows = em.createNativeQuery(escalationSql)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();

        java.util.Map<OffsetDateTime, Long> escalationByBucket = new java.util.HashMap<>();
        for (Object[] row : escalationRows) {
            escalationByBucket.put(toOffsetDateTime(row[0]), toLong(row[1]));
        }

        List<SlaTimelineBucket> out = new ArrayList<>(startedRows.size());
        for (Object[] row : startedRows) {
            OffsetDateTime bucketStart = toOffsetDateTime(row[0]);
            long started = toLong(row[1]);
            long resolved = toLong(row[2]);
            long escalations = escalationByBucket.getOrDefault(bucketStart, 0L);
            out.add(new SlaTimelineBucket(bucketStart, started, resolved, escalations));
        }
        return out;
    }

    private static void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from / to 不可為 null");
        }
        if (!from.isBefore(to)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "from 必須早於 to");
        }
    }

    private static String normalizeBucket(String bucket) {
        String lower = bucket == null ? "hour" : bucket.toLowerCase();
        if (!ALLOWED_BUCKETS.contains(lower)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST,
                    "bucket 僅支援 hour 或 day，收到 " + bucket);
        }
        return lower;
    }

    private static long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private static Double toDoubleOrNull(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    private static OffsetDateTime toOffsetDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof OffsetDateTime odt) return odt;
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        if (v instanceof java.time.Instant ins) {
            return ins.atOffset(java.time.ZoneOffset.UTC);
        }
        if (v instanceof java.time.LocalDateTime ldt) {
            return ldt.atOffset(java.time.ZoneOffset.UTC);
        }
        throw new IllegalStateException("無法轉換 timestamp 類型: " + v.getClass());
    }
}
