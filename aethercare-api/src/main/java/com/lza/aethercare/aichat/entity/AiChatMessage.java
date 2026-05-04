package com.lza.aethercare.aichat.entity;

import com.lza.aethercare.aichat.enums.ChatRole;
import com.lza.aethercare.aichat.enums.ChatSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Spec § AI_Care_Chat §8：AI Care Chat 對話訊息實體。
 *
 * <p>{@code structured_json} 存 ASSISTANT 回應的 questions / suggestedActions / dangerSigns
 * 等結構化資料，方便前端渲染按鈕；USER / SYSTEM 訊息為 null。
 */
@Entity
@Table(name = "ai_chat_messages")
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "workflow_id", nullable = false)
    private Long workflowId;

    @Column(name = "care_event_id", nullable = false)
    private Long careEventId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChatRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ChatSource source;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "structured_json", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String structuredJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
