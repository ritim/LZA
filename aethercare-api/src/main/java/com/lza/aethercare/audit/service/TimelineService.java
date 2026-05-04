package com.lza.aethercare.audit.service;

import com.lza.aethercare.audit.dto.TimelineItem;
import com.lza.aethercare.audit.dto.WorkflowTimelineResponse;
import com.lza.aethercare.audit.entity.CareAuditLog;
import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.repository.CareAuditLogRepository;
import com.lza.aethercare.userprofile.entity.AppUser;
import com.lza.aethercare.userprofile.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spec §6.8 timeline 組合服務：把內部 {@link CareAuditLog} 轉成 spec 結構化
 * {@link TimelineItem}，補上 actorName + level，避免前端散落映射邏輯。
 */
@Service
@RequiredArgsConstructor
public class TimelineService {

    private final CareAuditLogRepository auditRepo;
    private final AppUserRepository userRepo;

    @Transactional(readOnly = true)
    public WorkflowTimelineResponse buildForWorkflow(Long workflowId) {
        List<CareAuditLog> logs = auditRepo.findByWorkflowIdOrderByCreatedAtAsc(workflowId);
        Map<Long, String> actorNames = resolveActorNames(logs);
        List<TimelineItem> items = logs.stream()
                .map(l -> toItem(l, actorNames))
                .toList();
        return new WorkflowTimelineResponse(workflowId, items);
    }

    private Map<Long, String> resolveActorNames(List<CareAuditLog> logs) {
        List<Long> ids = logs.stream()
                .map(CareAuditLog::getActorId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) return Map.of();
        return userRepo.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        AppUser::getId,
                        u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                                ? u.getDisplayName()
                                : u.getUsername()
                ));
    }

    private TimelineItem toItem(CareAuditLog log, Map<Long, String> actorNames) {
        String actor = log.getActorId() == null
                ? "System"
                : actorNames.getOrDefault(log.getActorId(), "User#" + log.getActorId());
        return new TimelineItem(
                log.getId(),
                log.getCreatedAt(),
                log.getAction(),
                actor,
                log.getMessage(),
                deriveLevel(log.getAction())
        );
    }

    /** Spec §6.8：以 action type 推導前端可直接套色的 level。 */
    public static String deriveLevel(CareAuditAction action) {
        if (action == null) return "INFO";
        return switch (action) {
            case TASK_TIMEOUT, ESCALATION_TRIGGERED, TASK_ESCALATED -> "WARNING";
            case WORKFLOW_UNRESOLVED, STATE_CONFLICT_SKIPPED -> "CRITICAL";
            default -> "INFO";
        };
    }
}
