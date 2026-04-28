package com.lza.aethercare.audit.controller;

import com.lza.aethercare.audit.dto.AuditLogResponse;
import com.lza.aethercare.audit.entity.CareAuditLog;
import com.lza.aethercare.audit.service.CareAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 照護稽核 REST Controller：查詢指定 workflow 的完整事件時序。
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
@Slf4j
public class CareAuditController {

    private final CareAuditService careAuditService;

    /**
     * 查詢指定 workflow 的稽核日誌時序（依建立時間升序）。
     *
     * @param workflowId workflow 識別碼
     * @return 200 OK，稽核日誌清單
     */
    @GetMapping("/{workflowId}/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(@PathVariable Long workflowId) {
        log.info("查詢 audit timeline workflowId={}", workflowId);
        List<CareAuditLog> logs = careAuditService.timeline(workflowId);
        return ResponseEntity.ok(logs.stream().map(CareAuditController::toResponse).toList());
    }

    private static AuditLogResponse toResponse(CareAuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getAction(),
                log.getMessage(),
                log.getActorId(),
                log.getCreatedAt()
        );
    }
}
