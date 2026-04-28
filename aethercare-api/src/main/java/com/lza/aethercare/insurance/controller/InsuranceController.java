package com.lza.aethercare.insurance.controller;

import com.lza.aethercare.common.security.AppUserDetails;
import com.lza.aethercare.common.security.CurrentUser;
import com.lza.aethercare.insurance.dto.InsuranceEvidenceResponse;
import com.lza.aethercare.insurance.service.InsuranceEvidenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Insurance 對外 REST controller：保險業者查詢指定 elder 的照護證據。
 *
 * <p>受 INSURANCE role 保護（見 SecurityConfig）。預設 from = now-30d、to = now，
 * 比 SLA dashboard 預設窗口大，因為保險查詢通常涵蓋整個保單週期。
 */
@RestController
@RequestMapping("/api/v1/insurance")
@RequiredArgsConstructor
@Slf4j
public class InsuranceController {

    private final InsuranceEvidenceService evidenceService;

    @GetMapping("/evidence/{elderId}")
    public ResponseEntity<InsuranceEvidenceResponse> getEvidence(
            @PathVariable Long elderId,
            @RequestParam(value = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(value = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @CurrentUser AppUserDetails currentUser) {
        OffsetDateTime resolvedTo = to != null ? to : OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime resolvedFrom = from != null ? from : resolvedTo.minusDays(30);
        Long actorId = currentUser != null ? currentUser.getId() : null;
        log.info("保險查詢 elderId={} from={} to={} actor={}",
                elderId, resolvedFrom, resolvedTo, actorId);
        return ResponseEntity.ok(
                evidenceService.query(elderId, resolvedFrom, resolvedTo, actorId));
    }
}
