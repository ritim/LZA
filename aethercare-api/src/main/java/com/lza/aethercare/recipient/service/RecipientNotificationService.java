package com.lza.aethercare.recipient.service;

import com.lza.aethercare.notification.line.LineMessagingClient;
import com.lza.aethercare.notification.line.LineProperties;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import com.lza.aethercare.notification.service.StubNotificationGateway;
import com.lza.aethercare.userprofile.entity.CareContactEscalation;
import com.lza.aethercare.userprofile.entity.ElderContact;
import com.lza.aethercare.userprofile.enums.NotificationChannel;
import com.lza.aethercare.userprofile.repository.CareContactEscalationRepository;
import com.lza.aethercare.userprofile.repository.ElderContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Spec § Master §0 / Roadmap：被照顧者主動行為（check-in / 主動回報）後通知家屬的最小通路。
 *
 * <p>目前透過 {@link StubNotificationGateway} 只 log；Step 3 接真實 channel
 * （LINE Messaging API / FCM / email）時，替換 gateway 即可，呼叫端不變。
 *
 * <p>與 {@code NotificationService}（task-level 升級通知，綁 CareTask / 升級鏈）分離，
 * 避免把 caregiver-task 推播邏輯與 recipient-event 推播邏輯混在一起。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipientNotificationService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Taipei"));

    private final ElderContactRepository contactRepo;
    private final StubNotificationGateway gateway;
    /** LINE 啟用時才存在；用 ObjectProvider 避免 @ConditionalOnProperty 把 service 也擋掉。 */
    private final ObjectProvider<LineMessagingClient> lineClientProvider;
    private final LineProperties lineProps;
    private final CaregiverLineBindingRepository lineBindingRepo;
    private final CareContactEscalationRepository escalationRepo;

    /**
     * 通知家屬「被照顧者已完成今日 check-in」。所有 priority 順位的 contacts 都會收到，
     * 方便家族同步；caller 端不啟動 workflow（spec §0：CHECK_IN 不創 workflow）。
     */
    @Transactional(readOnly = true)
    public void notifyCheckInReceived(Long careRecipientId, OffsetDateTime occurredAt) {
        List<ElderContact> contacts = contactRepo.findByElderIdOrderByPriorityLevelAsc(careRecipientId);
        if (contacts.isEmpty()) {
            log.info("CheckIn 通知略過：recipient={} 無設定聯絡人", careRecipientId);
            return;
        }
        String localTime = TIME_FMT.format(occurredAt);
        String subject = "AetherCare 簽到通知";
        String body = String.format("被照顧者 %d 已於 %s 完成今日 check-in。",
                careRecipientId, localTime);
        for (ElderContact c : contacts) {
            // gateway 目前 stub log，保留作為可追蹤 audit 來源；ElderContact 暫無 preferred channel 欄位。
            gateway.send(NotificationChannel.LINE, c.getId(), subject, body);
        }
        log.info("CheckIn stub 通知已派發：recipient={} contactCount={}",
                careRecipientId, contacts.size());

        pushToLineRecipients(careRecipientId, body);
    }

    /**
     * 派送到 LINE：
     * <ul>
     *   <li>主要來源：依 {@code care_contact_escalation} 取此 elder 名下所有 caregiver，
     *       再查 {@code caregiver_line_binding} 取已綁 lineUserId — 多家庭場景能正確分流。</li>
     *   <li>Dev 補充：{@code aethercare.line.test-user-ids} 仍會收到（方便本機驗證；
     *       production 可空陣列關掉）。</li>
     * </ul>
     * 用 LinkedHashSet 去重避免綁定者 + 測試 ID 重複收。
     */
    private void pushToLineRecipients(Long careRecipientId, String body) {
        LineMessagingClient client = lineClientProvider.getIfAvailable();
        if (client == null) return;

        Set<String> targets = new LinkedHashSet<>();

        List<Long> caregiverIds = escalationRepo
                .findByElderIdAndEnabledTrueOrderByLevelAsc(careRecipientId).stream()
                .map(CareContactEscalation::getContactUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!caregiverIds.isEmpty()) {
            for (CaregiverLineBinding b : lineBindingRepo.findByCaregiverIdIn(caregiverIds)) {
                if (b.getLineUserId() != null && !b.getLineUserId().isBlank()) {
                    targets.add(b.getLineUserId());
                }
            }
        }

        if (lineProps != null && lineProps.testUserIds() != null) {
            for (String uid : lineProps.testUserIds()) {
                if (uid != null && !uid.isBlank()) targets.add(uid);
            }
        }
        if (targets.isEmpty()) return;

        for (String uid : targets) {
            client.pushText(uid, body);
        }
    }
}
