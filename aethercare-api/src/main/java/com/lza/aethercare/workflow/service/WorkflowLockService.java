package com.lza.aethercare.workflow.service;

import com.lza.aethercare.workflow.enums.CareWorkflowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Workflow 分散式鎖與 elder 最新狀態快取（Redis）。token-based 釋放避免誤刪他人的鎖。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowLockService {

    private static final String LOCK_KEY_PREFIX = "workflow:lock:";
    private static final String STATUS_KEY_PREFIX = "elder:latest-status:";

    /** 比對 token，相符才 DEL，避免 worker A 釋放掉 worker B 的鎖。 */
    private static final String RELEASE_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT =
            new DefaultRedisScript<>(RELEASE_LUA, Long.class);

    private final StringRedisTemplate redis;

    @Value("${aethercare.redis.workflow-lock-ttl-seconds:300}")
    private long lockTtlSeconds;

    @Value("${aethercare.redis.elder-status-ttl-seconds:3600}")
    private long elderStatusTtlSeconds;

    /** 取鎖成功時回傳 token；失敗回傳 empty。 */
    public Optional<String> acquire(Long workflowId) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(
                LOCK_KEY_PREFIX + workflowId, token, Duration.ofSeconds(lockTtlSeconds));
        return Boolean.TRUE.equals(ok) ? Optional.of(token) : Optional.empty();
    }

    /** 僅當 Redis 中的 value 與 token 相符時才釋放，token 不符代表鎖已被其他 worker 取代。 */
    public void release(Long workflowId, String token) {
        if (token == null) {
            return;
        }
        Long result = redis.execute(RELEASE_SCRIPT,
                List.of(LOCK_KEY_PREFIX + workflowId), token);
        if (result == null || result == 0L) {
            log.debug("workflow={} 鎖釋放被略過：token 不符或鎖已過期", workflowId);
        }
    }

    public void cacheElderLatestStatus(Long elderId, CareWorkflowStatus status) {
        redis.opsForValue().set(STATUS_KEY_PREFIX + elderId, status.name(),
                Duration.ofSeconds(elderStatusTtlSeconds));
    }

    public Optional<String> readElderLatestStatus(Long elderId) {
        return Optional.ofNullable(redis.opsForValue().get(STATUS_KEY_PREFIX + elderId));
    }
}
