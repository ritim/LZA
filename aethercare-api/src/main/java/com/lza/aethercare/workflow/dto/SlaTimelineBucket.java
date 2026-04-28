package com.lza.aethercare.workflow.dto;

import java.time.OffsetDateTime;

/**
 * SLA timeline 單一 bucket：對應一個 hour / day 的 workflow 與升級統計。
 *
 * <p>{@code bucketStart}：bucket 起始時間（已 truncate 到 hour / day 邊界，UTC）。
 */
public record SlaTimelineBucket(
        OffsetDateTime bucketStart,
        long workflowsStarted,
        long workflowsResolved,
        long escalations
) {
}
