package com.lza.aethercare.auth.service;

import com.lza.aethercare.auth.repository.RefreshTokenRepository;
import com.lza.aethercare.common.time.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Refresh token 清理 scheduler：每日凌晨刪除過期或已 revoked 超過保留期的 token，
 * 避免 refresh_token 表無限長大。
 *
 * <p>批次刪（每次 LIMIT batchSize）避免長鎖表；若一次跑沒清完，隔天會繼續清。
 */
@Component
@ConditionalOnProperty(
        name = "aethercare.security.jwt.refresh.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepo;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Value("${aethercare.security.jwt.refresh.cleanup.revoked-retention-days:7}")
    private int revokedRetentionDays;

    @Value("${aethercare.security.jwt.refresh.cleanup.batch-size:1000}")
    private int batchSize;

    @Value("${aethercare.security.jwt.refresh.cleanup.max-batches-per-run:50}")
    private int maxBatchesPerRun;

    private Counter rowsDeletedCounter;

    @PostConstruct
    public void init() {
        rowsDeletedCounter = Counter.builder("aethercare.refresh_token.cleanup.rows-deleted")
                .description("已刪除的 refresh token 數量（expired 或 revoked 超過保留期）")
                .register(meterRegistry);
    }

    /** cron 預設每日 03:30（UTC，避開業務尖峰，並錯開 outbox cleanup 03:15）。 */
    @Scheduled(cron = "${aethercare.security.jwt.refresh.cleanup.cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public void cleanup() {
        OffsetDateTime now = clock.now();
        OffsetDateTime revokedCutoff = now.minusDays(revokedRetentionDays);
        log.info("Refresh token cleanup 啟動：刪 expired<{} 或 revoked_at<{} (retention={}天)",
                now, revokedCutoff, revokedRetentionDays);

        int totalDeleted = 0;
        for (int i = 0; i < maxBatchesPerRun; i++) {
            int deleted = refreshTokenRepo.deleteExpiredOrOldRevoked(now, revokedCutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) break;
        }
        rowsDeletedCounter.increment(totalDeleted);
        log.info("Refresh token cleanup 完成：本次刪除 {} 筆", totalDeleted);
    }
}
