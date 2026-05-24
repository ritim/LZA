package com.lza.aethercare.notification.line.service;

import com.lza.aethercare.notification.line.LineMessagingClient;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Spec § Master §0：caregiver ↔ LINE userId 綁定邏輯。
 *
 * <p>流程：
 * <ol>
 *   <li>caregiver 在 dashboard 點「綁定 LINE」→ 呼叫 {@link #startBinding}
 *       產生 8 字元一次性碼，存 Redis（10 分鐘 TTL）</li>
 *   <li>caregiver 透過 LINE 私訊把碼傳給 OA</li>
 *   <li>webhook 收到 message event 呼叫 {@link #tryConsumeCode}，
 *       Redis getAndDelete 拿碼 → upsert {@code caregiver_line_binding}</li>
 * </ol>
 *
 * <p>綁定碼用 SecureRandom + 易讀字母表（去掉 I/O/0/1 等容易混淆字符）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LineBindingService {

    private static final String CODE_KEY_PREFIX = "line-binding-code:";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final int CODE_LENGTH = 8;
    /** 去掉 I O 0 1，避免使用者抄錯。 */
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final StringRedisTemplate redis;
    private final CaregiverLineBindingRepository repo;
    private final ObjectProvider<LineMessagingClient> lineClientProvider;
    private final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public Optional<CaregiverLineBinding> findBinding(Long caregiverId) {
        return repo.findByCaregiverId(caregiverId);
    }

    @Transactional
    public StartResult startBinding(Long caregiverId, Long tenantId) {
        String code;
        // 極小機率撞碼，最多重試 5 次。
        int retries = 0;
        do {
            code = generateCode();
            if (++retries > 5) {
                throw new IllegalStateException("LineBinding 連續 5 次撞碼，疑似 Redis 異常");
            }
        } while (Boolean.TRUE.equals(redis.hasKey(key(code))));

        String payload = tenantId + ":" + caregiverId;
        redis.opsForValue().set(key(code), payload, CODE_TTL);
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(CODE_TTL);
        log.info("LineBinding 碼已產生 caregiverId={} expiresAt={}", caregiverId, expiresAt);
        return new StartResult(code, expiresAt, (int) CODE_TTL.toMinutes());
    }

    @Transactional
    public Optional<Long> tryConsumeCode(String code, String lineUserId, String displayName) {
        if (code == null || lineUserId == null || lineUserId.isBlank()) return Optional.empty();
        String normalized = code.trim().toUpperCase();
        if (normalized.length() != CODE_LENGTH) return Optional.empty();

        String payload = redis.opsForValue().getAndDelete(key(normalized));
        if (payload == null) return Optional.empty();

        String[] parts = payload.split(":", 2);
        if (parts.length != 2) {
            log.warn("LineBinding payload 格式異常：{}", payload);
            return Optional.empty();
        }
        Long tenantId;
        Long caregiverId;
        try {
            tenantId = Long.valueOf(parts[0]);
            caregiverId = Long.valueOf(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("LineBinding payload 數值解析失敗：{}", payload);
            return Optional.empty();
        }

        // 若 caller 沒帶 displayName，呼叫 LINE Profile API 補（失敗 fallback null）。
        String resolvedDisplayName = displayName;
        if (resolvedDisplayName == null || resolvedDisplayName.isBlank()) {
            LineMessagingClient client = lineClientProvider.getIfAvailable();
            if (client != null) {
                resolvedDisplayName = client.fetchDisplayName(lineUserId).orElse(null);
            }
        }

        Optional<CaregiverLineBinding> existing = repo.findByCaregiverId(caregiverId);
        if (existing.isPresent()) {
            // 同一 caregiver 重新綁：覆蓋 lineUserId
            CaregiverLineBinding e = existing.get();
            e.setLineUserId(lineUserId);
            e.setLineDisplayName(resolvedDisplayName);
            e.setBoundAt(OffsetDateTime.now());
            repo.save(e);
        } else {
            // 檢查 lineUserId 是否已被別人綁
            Optional<CaregiverLineBinding> conflict = repo.findByLineUserId(lineUserId);
            if (conflict.isPresent()) {
                log.warn("LineBinding 衝突：lineUserId 已綁 otherCaregiver={}",
                        conflict.get().getCaregiverId());
                return Optional.empty();
            }
            repo.save(CaregiverLineBinding.builder()
                    .tenantId(tenantId)
                    .caregiverId(caregiverId)
                    .lineUserId(lineUserId)
                    .lineDisplayName(resolvedDisplayName)
                    .boundAt(OffsetDateTime.now())
                    .build());
        }
        log.info("LineBinding 完成 caregiverId={} displayName={}",
                caregiverId, resolvedDisplayName);
        return Optional.of(caregiverId);
    }

    @Transactional
    public boolean unbind(Long caregiverId) {
        Optional<CaregiverLineBinding> existing = repo.findByCaregiverId(caregiverId);
        if (existing.isEmpty()) return false;
        repo.deleteByCaregiverId(caregiverId);
        log.info("LineBinding 已解綁 caregiverId={}", caregiverId);
        return true;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private static String key(String code) {
        return CODE_KEY_PREFIX + code;
    }

    public record StartResult(String code, OffsetDateTime expiresAt, int ttlMinutes) {}
}
