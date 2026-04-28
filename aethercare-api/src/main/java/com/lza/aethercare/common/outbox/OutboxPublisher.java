package com.lza.aethercare.common.outbox;

import com.lza.aethercare.common.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/** Outbox scheduler：定期撈出 PENDING 訊息委派給 handler 逐筆 publish。
 *  拆 scheduler / handler 兩 bean 是為了讓 @Transactional 在 handler.publishOne 真正生效（避免 self-invocation）。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPublisher {

    private final OutboxMessageRepository outboxRepo;
    private final OutboxPublishHandler handler;
    private final Clock clock;

    @Value("${aethercare.outbox.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${aethercare.outbox.publish-fixed-delay:2000}")
    public void publishBatch() {
        List<OutboxMessage> ready = outboxRepo.findReadyToSend(clock.now(), batchSize);
        if (ready.isEmpty()) return;
        log.debug("Outbox batch: 撈到 {} 筆 PENDING", ready.size());
        for (OutboxMessage msg : ready) {
            try {
                handler.publishOne(msg.getId());
            } catch (Exception e) {
                log.warn("Outbox publish 失敗 id={} reason={}", msg.getId(), e.getMessage());
            }
        }
    }
}
