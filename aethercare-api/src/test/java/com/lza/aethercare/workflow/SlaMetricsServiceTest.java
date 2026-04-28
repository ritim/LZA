package com.lza.aethercare.workflow;

import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.workflow.dto.SlaSummaryResponse;
import com.lza.aethercare.workflow.service.SlaMetricsService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * {@link SlaMetricsService} 單元測試：以 mock EntityManager 驗證
 * (a) 純算術（rate 計算 / null 處理）與 (b) 區間驗證錯誤路徑。
 *
 * <p>真實 SQL 行為由整合測試（Testcontainers）覆蓋，這裡只驗服務邏輯。
 */
@ExtendWith(MockitoExtension.class)
class SlaMetricsServiceTest {

    @Mock
    EntityManager em;

    @Mock
    Query workflowQuery;
    @Mock
    Query escalationQuery;
    @Mock
    Query firstResponseQuery;

    SlaMetricsService service;

    private final OffsetDateTime from = OffsetDateTime.of(2026, 4, 20, 0, 0, 0, 0, ZoneOffset.UTC);
    private final OffsetDateTime to = OffsetDateTime.of(2026, 4, 27, 0, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new SlaMetricsService();
        ReflectionTestUtils.setField(service, "em", em);
    }

    /** 驗證 summary 算 resolvedRate / escalationRate 正確、null avg 維持 null。 */
    @Test
    void should_compute_summary_rates_correctly() {
        // workflow row: total=10 / resolved=7 / unresolved=2 / avg_resolve=120s
        Object[] wfRow = new Object[]{10L, 7L, 2L, 120.0};
        // escalation row: total=10 / escalated=3
        Object[] escRow = new Object[]{10L, 3L};
        // first response avg: null（無資料）
        Object firstResponse = null;

        given(em.createNativeQuery(anyString())).willReturn(workflowQuery, escalationQuery, firstResponseQuery);
        lenient().when(workflowQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(workflowQuery);
        lenient().when(escalationQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(escalationQuery);
        lenient().when(firstResponseQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(firstResponseQuery);
        given(workflowQuery.getSingleResult()).willReturn(wfRow);
        given(escalationQuery.getSingleResult()).willReturn(escRow);
        given(firstResponseQuery.getSingleResult()).willReturn(firstResponse);

        SlaSummaryResponse resp = service.summary(from, to);

        assertThat(resp.totalWorkflows()).isEqualTo(10);
        assertThat(resp.resolvedWorkflows()).isEqualTo(7);
        assertThat(resp.unresolvedWorkflows()).isEqualTo(2);
        assertThat(resp.resolvedRate()).isEqualTo(0.7);
        assertThat(resp.escalationRate()).isEqualTo(0.3);
        assertThat(resp.avgResolveSeconds()).isEqualTo(120.0);
        assertThat(resp.avgFirstResponseSeconds()).isNull();
    }

    /** 驗證 from >= to 時拋 BusinessException（INVALID_REQUEST）。 */
    @Test
    void should_reject_invalid_window() {
        assertThatThrownBy(() -> service.summary(to, from))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("from 必須早於 to");
    }
}
