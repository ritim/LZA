package com.lza.aethercare.notification.line.scheduler;

import com.lza.aethercare.notification.line.LineMessagingClient;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Spec § Master §0：caregiver_line_binding.line_display_name 定期 refresh。
 *
 * <p>使用者在 LINE 改暱稱後，本地 binding 表的 line_display_name 會永遠停留在綁定當下的快照值，
 * 造成 dashboard 顯示過時。此 scheduler 每日掃所有 binding，呼叫 LINE Profile API
 * 重抓 displayName 並寫回。
 *
 * <p>設計取捨：
 * <ul>
 *   <li>失敗（user blocked OA / API 暫時不可用）只 log，**不** unbind — 避免一次 API
 *       異常清光所有綁定。下次 scheduler 跑會再試。</li>
 *   <li>不在 push 之前順手 refresh：每次 push 多打一次 Profile API 會浪費配額，且
 *       LINE Profile API 跟 push 是同一個 rate limit bucket。</li>
 *   <li>LINE Profile API rate limit 2000 req/min；MVP binding 數遠小於此，先不加 throttle。</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(
        name = "aethercare.line.binding.refresh.enabled",
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LineBindingRefreshScheduler {

    private final CaregiverLineBindingRepository bindingRepo;
    /** LINE 未啟用時 client 不存在；用 ObjectProvider 容忍缺席。 */
    private final ObjectProvider<LineMessagingClient> lineClientProvider;
    private final MeterRegistry meterRegistry;

    private Counter updatedCounter;
    private Counter unchangedCounter;
    private Counter failedCounter;

    @PostConstruct
    public void init() {
        updatedCounter = Counter.builder("aethercare.line.binding.refresh.updated")
                .description("LINE displayName refresh 後實際更新到 DB 的 binding 數")
                .register(meterRegistry);
        unchangedCounter = Counter.builder("aethercare.line.binding.refresh.unchanged")
                .description("LINE displayName 與既有相同，未動 DB 的 binding 數")
                .register(meterRegistry);
        failedCounter = Counter.builder("aethercare.line.binding.refresh.failed")
                .description("呼叫 LINE Profile API 失敗或回 empty 的 binding 數")
                .register(meterRegistry);
    }

    /** 預設每日 04:15 UTC，錯開既有 03:15 outbox cleanup / 03:30 refresh token cleanup。 */
    @Scheduled(cron = "${aethercare.line.binding.refresh.cron:0 15 4 * * *}", zone = "UTC")
    @Transactional
    public void refreshAll() {
        LineMessagingClient client = lineClientProvider.getIfAvailable();
        if (client == null) {
            log.debug("LINE client 未啟用，跳過 binding refresh");
            return;
        }

        List<CaregiverLineBinding> all = bindingRepo.findAll();
        if (all.isEmpty()) {
            log.debug("沒有 LINE binding，跳過 refresh");
            return;
        }
        log.info("LINE binding refresh 啟動，共 {} 筆", all.size());

        int updated = 0, unchanged = 0, failed = 0;
        for (CaregiverLineBinding b : all) {
            try {
                Optional<String> fresh = client.fetchDisplayName(b.getLineUserId());
                if (fresh.isEmpty()) {
                    failed++;
                    log.info("LINE Profile API 回 empty caregiverId={} lineUserId={}",
                            b.getCaregiverId(), maskUserId(b.getLineUserId()));
                    continue;
                }
                String newName = fresh.get();
                if (Objects.equals(newName, b.getLineDisplayName())) {
                    unchanged++;
                    continue;
                }
                String oldName = b.getLineDisplayName();
                b.setLineDisplayName(newName);
                b.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                bindingRepo.save(b);
                updated++;
                log.info("LINE displayName 已更新 caregiverId={} '{}' → '{}'",
                        b.getCaregiverId(), oldName, newName);
            } catch (RuntimeException e) {
                failed++;
                log.warn("LINE binding refresh 例外 caregiverId={} err={}",
                        b.getCaregiverId(), e.toString());
            }
        }
        updatedCounter.increment(updated);
        unchangedCounter.increment(unchanged);
        failedCounter.increment(failed);
        log.info("LINE binding refresh 完成：updated={} unchanged={} failed={}",
                updated, unchanged, failed);
    }

    /** LINE userId 前 6 後 2 留出，中間以星號遮罩；避免 log 整顆 userId（PII）。 */
    private static String maskUserId(String userId) {
        if (userId == null || userId.length() < 10) return "***";
        return userId.substring(0, 6) + "***" + userId.substring(userId.length() - 2);
    }
}
