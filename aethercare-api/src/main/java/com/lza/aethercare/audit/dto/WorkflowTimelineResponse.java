package com.lza.aethercare.audit.dto;

import java.util.List;

/** Spec §6.8 GET /api/workflows/{id}/timeline 回應 wrapper。 */
public record WorkflowTimelineResponse(
        Long workflowId,
        List<TimelineItem> items
) {
}
