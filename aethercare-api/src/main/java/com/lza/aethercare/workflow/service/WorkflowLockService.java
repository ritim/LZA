package com.lza.aethercare.workflow.service;

import com.lza.aethercare.workflow.enums.CareWorkflowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/** Workflow 分散式鎖與 elder 最新狀態快取（Redis）。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowLockService {

    private final StringRedisTemplate redis;

    @Value("${aethercare.redis.workflow-lock-ttl-seconds:300}")
    private long lockTtlSeconds;

    @Value("${aethercare.redis.elder-status-ttl-seconds:3600}")
    private long elderStatusTtlSeconds;

    public boolean acquire(Long workflowId) {
        Boolean ok = redis.opsForValue().setIfAbsent(
                "workflow:lock:" + workflowId, "1", Duration.ofSeconds(lockTtlSeconds));
        return Boolean.TRUE.equals(ok);
    }

    public void release(Long workflowId) {
        redis.delete("workflow:lock:" + workflowId);
    }

    public void cacheElderLatestStatus(Long elderId, CareWorkflowStatus status) {
        redis.opsForValue().set("elder:latest-status:" + elderId, status.name(),
                Duration.ofSeconds(elderStatusTtlSeconds));
    }

    public Optional<String> readElderLatestStatus(Long elderId) {
        return Optional.ofNullable(redis.opsForValue().get("elder:latest-status:" + elderId));
    }
}
