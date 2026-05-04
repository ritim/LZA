package com.lza.aethercare.ai.service;

import com.lza.aethercare.ai.enums.KnowledgeEventType;
import com.lza.aethercare.event.enums.CareEventType;
import org.springframework.stereotype.Component;

/**
 * 將 v1.0-rc1 既有 {@link CareEventType} 對映到 AI 知識庫使用的
 * {@link KnowledgeEventType}。
 *
 * <p>對映原則：
 * <ul>
 *   <li>FALL_DETECTED / SOS → FALL（兩者都需要立即跌倒/急救式介入）</li>
 *   <li>NO_ACTIVITY / ACTIVITY_ANOMALY → NO_ACTIVITY</li>
 *   <li>DAILY_REMINDER → OTHER（無對應 knowledge）</li>
 * </ul>
 *
 * <p>STROKE / WANDERING / BREATHING_ISSUE 為 knowledge-only 類型，
 * 既有 enum 尚未支援，等 spec 真正新增 CareEventType 時擴充。
 */
@Component
public class EventTypeMapper {

    public KnowledgeEventType from(CareEventType type) {
        if (type == null) {
            return KnowledgeEventType.OTHER;
        }
        return switch (type) {
            case FALL_DETECTED, SOS -> KnowledgeEventType.FALL;
            case POSSIBLE_FALL -> KnowledgeEventType.POSSIBLE_FALL;
            case NO_ACTIVITY, ACTIVITY_ANOMALY -> KnowledgeEventType.NO_ACTIVITY;
            case MISSED_CHECK_IN -> KnowledgeEventType.MISSED_CHECK_IN;
            case NO_RESPONSE -> KnowledgeEventType.NO_RESPONSE;
            case FEELING_UNWELL -> KnowledgeEventType.FEELING_UNWELL;
            case DAILY_REMINDER -> KnowledgeEventType.OTHER;
        };
    }
}
