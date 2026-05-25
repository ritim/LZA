package com.lza.aethercare.event.repository;

import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.CareEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** 照護事件 repository。 */
@Repository
public interface CareEventRepository extends JpaRepository<CareEvent, Long> {

    List<CareEvent> findByElderIdOrderByOccurredAtDesc(Long elderId);

    /**
     * Missed-checkin scanner 去重用：今日是否已建過同型別事件。
     * 回 boolean 比 list 輕量，scanner 每分鐘觸發一次都不重。
     */
    boolean existsByElderIdAndEventTypeAndOccurredAtAfter(
            Long elderId, CareEventType eventType, OffsetDateTime occurredAt);

    /**
     * 短時窗 dedupe 用：給長輩 self-service「我需要幫忙 / 身體不舒服」防呆。
     * 抓 since 之後同 elder 同 type 最新一筆；存在代表是手抖連按。
     */
    Optional<CareEvent> findFirstByElderIdAndEventTypeAndCreatedAtAfterOrderByCreatedAtDesc(
            Long elderId, CareEventType eventType, OffsetDateTime since);
}
