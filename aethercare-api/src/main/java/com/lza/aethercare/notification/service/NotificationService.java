package com.lza.aethercare.notification.service;

import com.lza.aethercare.audit.enums.CareAuditAction;
import com.lza.aethercare.audit.service.CareAuditService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.common.event.PublishToKafka;
import com.lza.aethercare.common.time.Clock;
import com.lza.aethercare.event.entity.CareEvent;
import com.lza.aethercare.event.repository.CareEventRepository;
import com.lza.aethercare.notification.event.CareNotificationSentMessage;
import com.lza.aethercare.notification.line.LineMessagingClient;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import com.lza.aethercare.userprofile.entity.ElderProfile;
import com.lza.aethercare.userprofile.repository.ElderProfileRepository;
import com.lza.aethercare.userprofile.service.EscalationContactService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 通知 service：呼叫 stub gateway、寫 audit、發 Kafka。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final DateTimeFormatter DEADLINE_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.of("Asia/Taipei"));

    private final StubNotificationGateway gateway;
    private final CareAuditService auditService;
    private final EscalationContactService contactService;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    /** LINE 啟用時才存在；用 ObjectProvider 避免 @ConditionalOnProperty 把整條鏈也擋掉。 */
    private final ObjectProvider<LineMessagingClient> lineClientProvider;
    private final CaregiverLineBindingRepository lineBindingRepo;
    private final CareEventRepository careEventRepo;
    private final ElderProfileRepository elderProfileRepo;

    @Value("${aethercare.kafka.topics.notification-sent}")
    private String notificationSentTopic;

    @Transactional
    public void notify(CareTask task, Long elderId) {
        CareContactEscalation contact = contactService.findContact(elderId, task.getLevel())
                .orElseThrow(() -> new BusinessException(ErrorCode.ESCALATION_NOT_AVAILABLE,
                        "找不到 level=" + task.getLevel() + " 聯絡人"));
        String subject = "AetherCare Alert (level " + task.getLevel() + ")";
        String body = "task=" + task.getId() + " workflow=" + task.getWorkflowId();
        gateway.send(contact.getChannel(), task.getAssigneeId(), subject, body);

        auditService.log(task.getWorkflowId(), task.getEventId(), null,
                CareAuditAction.NOTIFICATION_SENT,
                "channel=" + contact.getChannel() + " assignee=" + task.getAssigneeId());

        publisher.publishEvent(new PublishToKafka(
                notificationSentTopic,
                String.valueOf(task.getWorkflowId()),
                new CareNotificationSentMessage(
                        task.getId(), task.getWorkflowId(),
                        contact.getChannel(), task.getAssigneeId(),
                        clock.now())));

        pushTaskToLine(task);
    }

    /**
     * 把 task-level 通知（升級鏈、SOS 派發）push 到對應 caregiver 的 LINE。
     * 用 Flex Message bubble 顯示事件 + SLA + 「我收到」按鈕（postback 觸發 ACKNOWLEDGE）。
     * 失敗只 log，不影響主 notify 流程（spec §0 通知派送失敗不該阻塞 workflow）。
     */
    private void pushTaskToLine(CareTask task) {
        LineMessagingClient client = lineClientProvider.getIfAvailable();
        if (client == null) return;
        if (task.getAssigneeId() == null) return;

        lineBindingRepo.findByCaregiverId(task.getAssigneeId())
                .ifPresent(binding -> {
                    Optional<CareEvent> eventOpt = task.getEventId() == null
                            ? Optional.empty() : careEventRepo.findById(task.getEventId());
                    Optional<ElderProfile> elderOpt = eventOpt
                            .map(CareEvent::getElderId)
                            .flatMap(elderProfileRepo::findById);
                    String altText = buildTaskAltText(task, eventOpt);
                    Map<String, Object> bubble = buildTaskFlex(task, eventOpt, elderOpt);
                    client.pushFlex(binding.getLineUserId(), altText, bubble);
                });
    }

    private String buildTaskAltText(CareTask task, Optional<CareEvent> eventOpt) {
        String typeText = eventOpt.map(e -> describeEventType(e.getEventType().name()))
                .orElse("照護事件");
        return String.format("【AetherCare %s】%s（任務 #%d，level %d）",
                isHighPriority(eventOpt) ? "緊急" : "通知",
                typeText, task.getId(), task.getLevel());
    }

    /** 建構 LINE Flex bubble JSON（Map 結構，序列化時自動成 JSON object）。 */
    private Map<String, Object> buildTaskFlex(CareTask task, Optional<CareEvent> eventOpt,
                                               Optional<ElderProfile> elderOpt) {
        boolean high = isHighPriority(eventOpt);
        String typeText = eventOpt.map(e -> describeEventType(e.getEventType().name()))
                .orElse("照護事件");
        String riskText = eventOpt.map(e -> e.getRiskLevel().name()).orElse("?");
        String deadlineText = task.getDeadlineAt() == null
                ? "—"
                : DEADLINE_FMT.format(task.getDeadlineAt());
        long remainingSec = task.getDeadlineAt() == null
                ? -1
                : Duration.between(OffsetDateTime.now(), task.getDeadlineAt()).getSeconds();
        String remainingText = remainingSec < 0
                ? "已過期"
                : "剩約 " + remainingSec + " 秒";
        String headerEmoji = high ? "🚨" : "🔔";
        String headerBg = high ? "#F56C6C" : "#409EFF";
        String urgencyTag = high ? "緊急" : "通知";

        Map<String, Object> bubble = new LinkedHashMap<>();
        bubble.put("type", "bubble");

        // header
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("type", "box");
        header.put("layout", "vertical");
        header.put("backgroundColor", headerBg);
        header.put("paddingAll", "14px");
        header.put("contents", List.of(
                Map.of("type", "text",
                        "text", headerEmoji + " " + typeText,
                        "color", "#FFFFFF", "weight", "bold", "size", "lg"),
                Map.of("type", "text",
                        "text", "AetherCare " + urgencyTag + "通報",
                        "color", "#FFFFFFCC", "size", "xs", "margin", "xs")));
        bubble.put("header", header);

        // body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "box");
        body.put("layout", "vertical");
        body.put("spacing", "sm");
        body.put("paddingAll", "14px");
        body.put("contents", List.of(
                Map.of("type", "text", "text", "風險等級：" + riskText,
                        "size", "sm", "color", "#606266"),
                Map.of("type", "text",
                        "text", "任務 #" + task.getId() + " · level " + task.getLevel(),
                        "size", "sm", "color", "#606266"),
                Map.of("type", "text",
                        "text", "SLA " + deadlineText + "（" + remainingText + "）",
                        "size", "sm", "color", "#909399", "wrap", true)));
        bubble.put("body", body);

        // footer：先「📞 打給長輩」（若有 phone）→ 再「我收到了」postback
        Map<String, Object> footer = new LinkedHashMap<>();
        footer.put("type", "box");
        footer.put("layout", "vertical");
        footer.put("spacing", "xs");
        footer.put("paddingAll", "10px");

        java.util.List<Map<String, Object>> buttons = new java.util.ArrayList<>();
        elderOpt.filter(e -> e.getPhone() != null && !e.getPhone().isBlank())
                .ifPresent(elder -> buttons.add(buildCallElderButton(elder)));
        buttons.add(buildAckButton(task));
        footer.put("contents", buttons);
        bubble.put("footer", footer);

        return bubble;
    }

    /** 「📞 打給長輩」按鈕 — LINE 收到後直接喚起手機撥號。 */
    private static Map<String, Object> buildCallElderButton(ElderProfile elder) {
        String label = "📞 打給" + safeShortName(elder.getName());
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "uri");
        action.put("label", label);
        action.put("uri", "tel:" + elder.getPhone().replaceAll("\\s+", ""));
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("type", "button");
        button.put("style", "primary");
        button.put("color", "#E6A23C");
        button.put("height", "sm");
        button.put("action", action);
        return button;
    }

    /** 「我收到了」postback 按鈕 — webhook 接收後觸發 ACKNOWLEDGE。 */
    private static Map<String, Object> buildAckButton(CareTask task) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "postback");
        action.put("label", "我收到了");
        action.put("data", "ack:" + task.getId());
        action.put("displayText", "我收到任務 #" + task.getId());
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("type", "button");
        button.put("style", "primary");
        button.put("color", "#67C23A");
        button.put("height", "sm");
        button.put("action", action);
        return button;
    }

    /** Flex button label 最長 20 字（LINE 規範）；過長截斷避免 push 失敗。 */
    private static String safeShortName(String name) {
        if (name == null || name.isBlank()) return "長輩";
        // 「📞 打給」3 字 + emoji，扣掉後留 17 全寬字元給名字（保守取 10 半形寬）
        return name.length() > 10 ? name.substring(0, 10) : name;
    }

    private static boolean isHighPriority(Optional<CareEvent> ev) {
        return ev.map(e -> {
            String r = e.getRiskLevel() == null ? "" : e.getRiskLevel().name();
            return "HIGH".equals(r) || "CRITICAL".equals(r);
        }).orElse(false);
    }

    private static String describeEventType(String type) {
        return switch (type) {
            case "SOS" -> "緊急求助 SOS";
            case "MISSED_CHECK_IN" -> "未完成簽到";
            case "NO_ACTIVITY" -> "長時間無活動";
            case "FALL_DETECTED" -> "疑似跌倒";
            case "POSSIBLE_FALL" -> "可能跌倒";
            case "FEELING_UNWELL" -> "身體不適";
            case "NO_RESPONSE" -> "無回應";
            case "DAILY_REMINDER" -> "每日提醒";
            default -> type;
        };
    }
}
