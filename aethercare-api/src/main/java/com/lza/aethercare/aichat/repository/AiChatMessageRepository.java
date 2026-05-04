package com.lza.aethercare.aichat.repository;

import com.lza.aethercare.aichat.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/** AI Care Chat 訊息 repository。 */
@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    /** 載入 workflow chat 歷史，按時間升冪排序給前端渲染。 */
    List<AiChatMessage> findByWorkflowIdOrderByCreatedAtAsc(Long workflowId);

    /** 判斷是否已存在 SYSTEM 開場 / STATIC_GUIDANCE 首訊息，避免每次開啟事件重複產生。 */
    boolean existsByWorkflowId(Long workflowId);
}
