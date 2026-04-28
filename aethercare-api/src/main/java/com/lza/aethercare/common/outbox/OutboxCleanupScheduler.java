package com.lza.aethercare.common.outbox;

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
 * Outbox 清理 scheduler：每日凌晨刪除 PUBLISHED 且超過保留期的訊息，
 * 避免 outbox_message 無限長大。DEAD_LETTER 保留供 troubleshooting。
 *
 * <p>批次刪（每次 LIMIT batchSize）避免長鎖表；若一次跑沒清完，
 * 隔天會繼續清。
 */
@Component
@ConditionalOnProperty(
        name = "aethercare.outbox.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OutboxCleanupScheduler {

    private final OutboxMessageRepository outboxRepo;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Value("${aethercare.outbox.cleanup.retention-days:7}")
    private int retentionDays;

    @Value("${aethercare.outbox.cleanup.batch-size:1000}")
    private int batchSize;

    @Value("${aethercare.outbox.cleanup.max-batches-per-run:50}")
    private int maxBatchesPerRun;

    private Counter rowsDeletedCounter;

    @PostConstruct
    public void init() {
        rowsDeletedCounter = Counter.builder("aethercare.outbox.cleanup.rows-deleted")
                .description("已刪除的 outbox PUBLISHED 訊息數量")
                .register(meterRegistry);
    }

    /**
     * cron 預設每日 03:15（UTC，避開業務尖峰）。
     * 可由 aethercare.outbox.cleanup.cron 環境變數覆蓋。
     */
    @Scheduled(cron = "${aethercare.outbox.cleanup.cron:0 15 3 * * *}", zone = "UTC")
    @Transactional
    public void cleanup() {
        OffsetDateTime cutoff = clock.now().minusDays(retentionDays);
        log.info("Outbox cleanup 啟動：刪 PUBLISHED 且 sent_at < {} (retention={}天)", cutoff, retentionDays);

        int totalDeleted = 0;
        for (int i = 0; i < maxBatchesPerRun; i++) {
            int deleted = outboxRepo.deletePublishedBefore(cutoff, batchSize);
            totalDeleted += deleted;
            if (deleted < batchSize) break;
        }
        rowsDeletedCounter.increment(totalDeleted);
        log.info("Outbox cleanup 完成：本次刪除 {} 筆", totalDeleted);
    }
}
