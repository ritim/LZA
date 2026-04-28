package com.lza.aethercare.action.controller;

import com.lza.aethercare.action.dto.CareActionResponse;
import com.lza.aethercare.action.dto.CreateCareActionRequest;
import com.lza.aethercare.action.entity.CareAction;
import com.lza.aethercare.action.service.CareActionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 照護動作 REST Controller：接收使用者回填並驅動任務／流程狀態變更。
 */
@RestController
@RequestMapping("/api/v1/care-tasks")
@RequiredArgsConstructor
@Slf4j
public class CareActionController {

    private final CareActionService careActionService;

    /**
     * 對指定任務提交照護動作（如 CONFIRM_SAFE、NEED_HELP、ACKNOWLEDGE）。
     *
     * @param taskId 任務識別碼
     * @param req    動作請求內容
     * @return 201 Created，含已建立的動作識別資訊
     */
    @PostMapping("/{taskId}/actions")
    public ResponseEntity<CareActionResponse> handleAction(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateCareActionRequest req) {
        log.info("收到任務動作 taskId={} actionType={} actorId={}", taskId, req.getActionType(), req.getActorId());
        CareAction action = careActionService.handle(taskId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(action));
    }

    private static CareActionResponse toResponse(CareAction action) {
        return new CareActionResponse(
                action.getId(),
                action.getTaskId(),
                action.getWorkflowId(),
                action.getActorId(),
                action.getActionType(),
                action.getCreatedAt()
        );
    }
}
