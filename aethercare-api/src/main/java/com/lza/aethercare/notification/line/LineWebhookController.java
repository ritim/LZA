package com.lza.aethercare.notification.line;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.action.dto.CreateCareActionRequest;
import com.lza.aethercare.action.enums.CareActionType;
import com.lza.aethercare.action.service.CareActionService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import com.lza.aethercare.notification.line.service.LineBindingService;
import com.lza.aethercare.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Spec § Master §0：LINE Messaging API webhook 接收端（階段 A）。
 *
 * <p>本階段僅 log event（特別是 {@code follow} 事件的 userId），供開發者手動把家屬 userId
 * 抄到 {@code aethercare.line.test-user-ids}。階段 B 會接 binding service / DB 表自動關聯。
 *
 * <p>LINE 強制：
 * <ul>
 *   <li>webhook URL 必須 HTTPS。本機開發用 cloudflared / ngrok tunnel。</li>
 *   <li>必須驗證 {@code X-Line-Signature}（HMAC-SHA256 with channel-secret）。
 *       簽章不符視為偽造請求，回 401。</li>
 *   <li>應在 1 秒內回 200；否則 LINE 會重送並標記 channel 為不穩定。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/line")
@RequiredArgsConstructor
@Slf4j
public class LineWebhookController {

    private static final int BINDING_CODE_LENGTH = 8;

    private final LineProperties props;
    private final ObjectMapper objectMapper;
    private final LineBindingService bindingService;
    private final ObjectProvider<LineMessagingClient> lineClientProvider;
    private final CaregiverLineBindingRepository bindingRepo;
    private final CareActionService careActionService;

    /** 健康檢查：方便瀏覽器手動測連線。LINE 本身一律 POST。 */
    @GetMapping("/webhook")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("AetherCare LINE webhook is alive");
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "X-Line-Signature", required = false) String signature,
            @RequestBody(required = false) String body) {
        if (!verifySignature(signature, body)) {
            log.warn("LINE webhook 簽章驗證失敗，丟棄請求");
            return ResponseEntity.status(401).build();
        }
        if (body == null || body.isBlank()) {
            return ResponseEntity.ok().build();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode events = root.path("events");
            for (JsonNode ev : events) {
                handleEvent(ev);
            }
        } catch (RuntimeException | java.io.IOException e) {
            log.warn("LINE webhook payload 解析失敗：{}", e.toString());
        }
        return ResponseEntity.ok().build();
    }

    private void handleEvent(JsonNode ev) {
        String type = ev.path("type").asText("?");
        String userId = ev.path("source").path("userId").asText(null);
        switch (type) {
            case "follow" -> log.info("[LINE-WEBHOOK] follow userId={}", userId);
            case "unfollow" -> log.info("[LINE-WEBHOOK] unfollow userId={}", userId);
            case "message" -> handleMessage(ev, userId);
            case "postback" -> handlePostback(ev, userId);
            default -> log.info("[LINE-WEBHOOK] event={} userId={}", type, userId);
        }
    }

    /**
     * Postback：處理 Flex Message「我收到了」按鈕（data={@code ack:<taskId>}）。
     *
     * <p>實接 {@link CareActionService#handle}：以 binding 表反查 caregiverId / tenantId，
     * 觸發 ACKNOWLEDGE 內部語意 — task 由 PENDING → ACKNOWLEDGED、寫 CareAction、audit、kafka。
     * 不會把 workflow 結案（依 spec § Gap D「保留 workflow open」族），caregiver 仍需到 dashboard
     * 做最後 CONFIRM_SAFE / ESCALATE。
     */
    private void handlePostback(JsonNode ev, String userId) {
        String data = ev.path("postback").path("data").asText("");
        String replyToken = ev.path("replyToken").asText(null);
        log.info("[LINE-WEBHOOK] postback userId={} data={}", userId, data);
        if (!data.startsWith("ack:") || userId == null) return;

        LineMessagingClient client = lineClientProvider.getIfAvailable();
        if (client == null || replyToken == null) return;

        Long taskId = parseTaskId(data.substring(4).trim());
        if (taskId == null) {
            client.replyText(replyToken, "❌ 無法解析任務編號，請至 Dashboard 處理。");
            return;
        }

        Optional<CaregiverLineBinding> bindingOpt = bindingRepo.findByLineUserId(userId);
        if (bindingOpt.isEmpty()) {
            client.replyText(replyToken,
                    "⚠️ 您的 LINE 尚未綁定任何照護者帳號，無法回應任務。"
                            + "請至 AetherCare Dashboard 取得綁定碼。");
            return;
        }
        CaregiverLineBinding binding = bindingOpt.get();

        String reply = tryAcknowledge(binding, taskId);
        client.replyText(replyToken, reply);
    }

    /** 在綁定的 tenant scope 下執行 ACKNOWLEDGE；finally 清掉 ThreadLocal 避免污染 thread pool。 */
    private String tryAcknowledge(CaregiverLineBinding binding, Long taskId) {
        TenantContext.set(binding.getTenantId());
        try {
            CreateCareActionRequest req = CreateCareActionRequest.builder()
                    .actionType(CareActionType.ACKNOWLEDGE)
                    .note("LINE postback ack (caregiverId=" + binding.getCaregiverId() + ")")
                    .build();
            careActionService.handle(taskId, binding.getCaregiverId(), req);
            log.info("[LINE-WEBHOOK] ACKNOWLEDGE 成功 taskId={} caregiverId={}",
                    taskId, binding.getCaregiverId());
            return "✅ 已收到任務 #" + taskId + "。請至 AetherCare Dashboard 完成後續處理。";
        } catch (BusinessException e) {
            log.info("[LINE-WEBHOOK] ACKNOWLEDGE 拒絕 taskId={} caregiverId={} code={} msg={}",
                    taskId, binding.getCaregiverId(), e.getCode(), e.getMessage());
            if (e.getCode() == ErrorCode.TASK_ALREADY_FINALIZED) {
                return "ℹ️ 此任務 #" + taskId + " 已被處理，無需再回應。";
            }
            if (e.getCode() == ErrorCode.NOT_FOUND) {
                return "❌ 找不到任務 #" + taskId + "，可能已被移除。";
            }
            return "⚠️ 系統暫時無法處理，請至 Dashboard 操作（任務 #" + taskId + "）。";
        } catch (RuntimeException e) {
            log.warn("[LINE-WEBHOOK] ACKNOWLEDGE 例外 taskId={} caregiverId={}",
                    taskId, binding.getCaregiverId(), e);
            return "⚠️ 系統異常，請至 Dashboard 操作（任務 #" + taskId + "）。";
        } finally {
            TenantContext.clear();
        }
    }

    private static Long parseTaskId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Message event：若內容看起來是綁定碼，嘗試消耗碼並回覆綁定結果；
     * 不是綁定碼則僅 log（之後可擴 echo / FAQ）。
     */
    private void handleMessage(JsonNode ev, String userId) {
        String msgType = ev.path("message").path("type").asText("?");
        String text = ev.path("message").path("text").asText("");
        String replyToken = ev.path("replyToken").asText(null);
        log.info("[LINE-WEBHOOK] message userId={} type={} text={}", userId, msgType, text);

        if (!"text".equals(msgType) || userId == null) return;
        if (!looksLikeBindingCode(text)) return;

        Optional<Long> bound = bindingService.tryConsumeCode(text, userId, null);
        LineMessagingClient client = lineClientProvider.getIfAvailable();
        if (client == null || replyToken == null) return;

        if (bound.isPresent()) {
            client.replyText(replyToken,
                    "✅ 綁定成功！未來會在這裡收到家中長輩的簽到與緊急通報訊息。");
        } else {
            client.replyText(replyToken,
                    "❌ 綁定碼無效或已過期。請回到 AetherCare Dashboard 重新申請綁定碼。");
        }
    }

    /** 8 字元 A-Z / 2-9（去掉 I O 0 1，跟 LineBindingService 的字母表一致）。 */
    private static boolean looksLikeBindingCode(String text) {
        if (text == null) return false;
        String t = text.trim().toUpperCase();
        if (t.length() != BINDING_CODE_LENGTH) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            boolean ok = (c >= 'A' && c <= 'Z' && c != 'I' && c != 'O')
                    || (c >= '2' && c <= '9');
            if (!ok) return false;
        }
        return true;
    }

    private boolean verifySignature(String signature, String body) {
        if (signature == null || signature.isBlank()) return false;
        String secret = props.channelSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("LINE channel-secret 未設定，無法驗證 webhook 簽章");
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(bytes));
            return constantTimeEquals(expected, signature);
        } catch (Exception e) {
            log.warn("LINE 簽章計算失敗：{}", e.toString());
            return false;
        }
    }

    /** 常數時間比較，避免 timing attack 反推簽章。 */
    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
