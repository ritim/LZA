package com.lza.aethercare.dashboard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.anomaly.enums.ActivityType;
import com.lza.aethercare.anomaly.repository.ElderActivityEventRepository;
import com.lza.aethercare.audit.entity.CareAuditLog;
import com.lza.aethercare.audit.repository.CareAuditLogRepository;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.dashboard.dto.DashboardResponse;
import com.lza.aethercare.dashboard.dto.DashboardResponse.ActiveEventItem;
import com.lza.aethercare.dashboard.dto.DashboardResponse.ElderRef;
import com.lza.aethercare.dashboard.dto.DashboardResponse.SlaInfo;
import com.lza.aethercare.dashboard.dto.DashboardResponse.Summary;
import com.lza.aethercare.dashboard.dto.DashboardResponse.TimelineItem;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.enums.RiskLevel;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.repository.CareTaskRepository;
import com.lza.aethercare.userprofile.entity.ElderProfile;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
import com.lza.aethercare.workflow.entity.CareWorkflowInstance;
import com.lza.aethercare.workflow.enums.CareWorkflowStatus;
import com.lza.aethercare.workflow.repository.CareWorkflowInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Spec §6.1 caregiver dashboard 聚合 service。
 *
 * <p>策略：找出 caregiver 名下 PENDING / ACKNOWLEDGED 的任務，攤平到對應 event + workflow + elder，
 * 由 task.deadline_at 算 SLA 倒數。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final CareTaskRepository taskRepo;
    private final CareWorkflowInstanceRepository workflowRepo;
    private final CareEventRepository eventRepo;
    private final ElderProfileRepository elderRepo;
    private final CareAuditLogRepository auditRepo;
    private final ElderActivityEventRepository activityRepo;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    private static final int RECENT_TIMELINE_LIMIT = 10;
    /** Spec § Master §0：demo 時區固定 Asia/Taipei；resolved-today 比對用。 */
    private static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Taipei");

    @Transactional(readOnly = true)
    public DashboardResponse buildFor(Long caregiverId) {
        OffsetDateTime now = clock.now();

        List<CareTask> activeTasks = caregiverId == null
                ? List.of()
                : taskRepo.findActiveByAssignee(caregiverId);

        // 預取相關 workflow / event / elder，避免 N+1
        List<Long> workflowIds = activeTasks.stream().map(CareTask::getWorkflowId).distinct().toList();
        Map<Long, CareWorkflowInstance> workflowsById = workflowRepo.findAllById(workflowIds).stream()
                .collect(java.util.stream.Collectors.toMap(CareWorkflowInstance::getId, w -> w));

        List<Long> eventIds = activeTasks.stream().map(CareTask::getEventId).distinct().toList();
        Map<Long, CareEvent> eventsById = eventRepo.findAllById(eventIds).stream()
                .collect(java.util.stream.Collectors.toMap(CareEvent::getId, e -> e));

        List<Long> elderIds = eventsById.values().stream().map(CareEvent::getElderId).distinct().toList();
        Map<Long, ElderProfile> eldersById = elderRepo.findAllById(elderIds).stream()
                .collect(java.util.stream.Collectors.toMap(ElderProfile::getId, e -> e));

        List<ActiveEventItem> active = new ArrayList<>();
        int normal = 0, attention = 0, alert = 0;
        int expiredTaskCount = 0;
        OffsetDateTime nextDeadline = null;
        Set<Long> waitingWorkflowIds = new HashSet<>();
        Set<Long> activeEventIds = new HashSet<>();
        for (CareTask t : activeTasks) {
            CareEvent ev = eventsById.get(t.getEventId());
            CareWorkflowInstance wf = workflowsById.get(t.getWorkflowId());
            if (ev == null || wf == null) continue;

            ElderProfile elder = eldersById.get(ev.getElderId());
            ElderRef elderRef = elder == null
                    ? new ElderRef(ev.getElderId(), null, null)
                    : new ElderRef(elder.getId(), elder.getName(), elder.getAge());

            SlaInfo sla = buildSla(t, now);
            String location = parseLocation(ev.getMetadata());

            active.add(new ActiveEventItem(
                    ev.getId(),
                    wf.getId(),
                    elderRef,
                    ev.getEventType(),
                    ev.getRiskLevel(),
                    wf.getStatus().name(),
                    location,
                    ev.getOccurredAt(),
                    sla
            ));

            switch (ev.getRiskLevel() == null ? RiskLevel.LOW : ev.getRiskLevel()) {
                case HIGH -> alert++;
                case MEDIUM -> attention++;
                default -> normal++;
            }
            activeEventIds.add(ev.getId());
            if (wf.getStatus() == CareWorkflowStatus.WAITING_RESPONSE) {
                waitingWorkflowIds.add(wf.getId());
            }
            if (sla != null) {
                if (sla.expired()) expiredTaskCount++;
                if (t.getDeadlineAt() != null
                        && (nextDeadline == null || t.getDeadlineAt().isBefore(nextDeadline))) {
                    nextDeadline = t.getDeadlineAt();
                }
            }
        }

        active.sort(Comparator
                .comparingLong((ActiveEventItem a) -> a.sla() == null ? Long.MAX_VALUE : a.sla().remainingSeconds())
                .thenComparing(ActiveEventItem::detectedAt));

        List<TimelineItem> timeline = recentTimeline();
        Summary summary = new Summary(
                normal, attention, alert,
                activeEventIds.size(),
                waitingWorkflowIds.size(),
                expiredTaskCount,
                workflowRepo.countResolvedSince(startOfDay(now)),
                latestActivityFor(elderIds, ActivityType.CHECK_IN),
                latestActivityFor(elderIds, null),
                nextDeadline
        );
        return new DashboardResponse(summary, active, timeline);
    }

    /** caregiver 名下 recipient 集合最新一筆活動時間；type=null 代表任意 type。 */
    private OffsetDateTime latestActivityFor(List<Long> elderIds, ActivityType type) {
        if (elderIds == null || elderIds.isEmpty()) return null;
        return (type == null
                ? activityRepo.findLatestByElderIds(elderIds)
                : activityRepo.findLatestByElderIdsAndType(elderIds, type.name()))
                .map(e -> e.getOccurredAt())
                .orElse(null);
    }

    private OffsetDateTime startOfDay(OffsetDateTime now) {
        return now.atZoneSameInstant(DEMO_ZONE)
                .toLocalDate()
                .atStartOfDay(DEMO_ZONE)
                .toOffsetDateTime();
    }

    private SlaInfo buildSla(CareTask t, OffsetDateTime now) {
        if (t.getDeadlineAt() == null) return null;
        Duration left = Duration.between(now, t.getDeadlineAt());
        long secs = left.getSeconds();
        boolean expired = left.isNegative() || left.isZero();
        return new SlaInfo(t.getDeadlineAt(), Math.max(secs, 0), expired);
    }

    private String parseLocation(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            JsonNode loc = node.get("location");
            if (loc == null || loc.isNull()) return null;
            return loc.asText();
        } catch (IOException ex) {
            return null;
        }
    }

    private List<TimelineItem> recentTimeline() {
        List<CareAuditLog> recent = auditRepo.findAll(
                PageRequest.of(0, RECENT_TIMELINE_LIMIT,
                        org.springframework.data.domain.Sort.by("createdAt").descending())
        ).getContent();
        return recent.stream()
                .map(l -> new TimelineItem(l.getCreatedAt(), Optional.ofNullable(l.getMessage()).orElse("")))
                .toList();
    }
}
