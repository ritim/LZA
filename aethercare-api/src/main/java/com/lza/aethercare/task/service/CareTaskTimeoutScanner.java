package com.lza.aethercare.task.service;

import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.task.entity.CareTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** 排程器：定期掃描超時 task，逐筆委派給 handler 走獨立 transaction。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CareTaskTimeoutScanner {

    private final CareTaskService taskService;
    private final CareTaskTimeoutHandler handler;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${aethercare.scheduler.timeout-scan-fixed-delay:5000}")
    public void scan() {
        List<CareTask> expired = taskService.findExpiredPending(clock.now());
        if (expired.isEmpty()) return;
        log.debug("掃描到 {} 個過期 task", expired.size());
        for (CareTask t : expired) {
            try {
                handler.handleTimeout(t.getId());
            } catch (Exception e) {
                log.warn("處理 timeout 失敗 taskId={} reason={}", t.getId(), e.getMessage());
            }
        }
    }
}
