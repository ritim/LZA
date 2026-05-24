package com.lza.aethercare.notification.line;

import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import com.lza.aethercare.notification.line.scheduler.LineBindingRefreshScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * LineBindingRefreshScheduler 單元測試 — 覆蓋 5 條主路徑。
 */
@ExtendWith(MockitoExtension.class)
class LineBindingRefreshSchedulerTest {

    @Mock CaregiverLineBindingRepository bindingRepo;
    @Mock ObjectProvider<LineMessagingClient> lineClientProvider;
    @Mock LineMessagingClient lineClient;

    LineBindingRefreshScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new LineBindingRefreshScheduler(
                bindingRepo, lineClientProvider, new SimpleMeterRegistry());
        scheduler.init();
    }

    @Test
    void should_update_db_when_display_name_changed() {
        when(lineClientProvider.getIfAvailable()).thenReturn(lineClient);
        CaregiverLineBinding b = newBinding(1L, 2L, "Uxxx", "舊暱稱");
        given(bindingRepo.findAll()).willReturn(List.of(b));
        given(lineClient.fetchDisplayName("Uxxx")).willReturn(Optional.of("新暱稱"));

        scheduler.refreshAll();

        ArgumentCaptor<CaregiverLineBinding> saved = ArgumentCaptor.forClass(CaregiverLineBinding.class);
        then(bindingRepo).should().save(saved.capture());
        assertThat(saved.getValue().getLineDisplayName()).isEqualTo("新暱稱");
    }

    @Test
    void should_not_update_db_when_display_name_unchanged() {
        when(lineClientProvider.getIfAvailable()).thenReturn(lineClient);
        CaregiverLineBinding b = newBinding(1L, 2L, "Uxxx", "暱稱");
        given(bindingRepo.findAll()).willReturn(List.of(b));
        given(lineClient.fetchDisplayName("Uxxx")).willReturn(Optional.of("暱稱"));

        scheduler.refreshAll();

        then(bindingRepo).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_skip_when_profile_api_returns_empty() {
        when(lineClientProvider.getIfAvailable()).thenReturn(lineClient);
        CaregiverLineBinding b = newBinding(1L, 2L, "Uxxx", "暱稱");
        given(bindingRepo.findAll()).willReturn(List.of(b));
        given(lineClient.fetchDisplayName("Uxxx")).willReturn(Optional.empty());

        scheduler.refreshAll();

        then(bindingRepo).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void should_continue_other_bindings_when_one_throws() {
        when(lineClientProvider.getIfAvailable()).thenReturn(lineClient);
        CaregiverLineBinding b1 = newBinding(1L, 2L, "U1", "A");
        CaregiverLineBinding b2 = newBinding(2L, 3L, "U2", "B");
        given(bindingRepo.findAll()).willReturn(List.of(b1, b2));
        willThrow(new RuntimeException("LINE 5xx"))
                .given(lineClient).fetchDisplayName("U1");
        given(lineClient.fetchDisplayName("U2")).willReturn(Optional.of("B-new"));

        scheduler.refreshAll();

        // b1 失敗不會 save；b2 成功 update
        ArgumentCaptor<CaregiverLineBinding> saved = ArgumentCaptor.forClass(CaregiverLineBinding.class);
        then(bindingRepo).should().save(saved.capture());
        assertThat(saved.getValue().getCaregiverId()).isEqualTo(3L);
        assertThat(saved.getValue().getLineDisplayName()).isEqualTo("B-new");
    }

    @Test
    void should_noop_when_line_client_unavailable() {
        when(lineClientProvider.getIfAvailable()).thenReturn(null);

        scheduler.refreshAll();

        then(bindingRepo).should(never()).findAll();
    }

    private static CaregiverLineBinding newBinding(Long id, Long caregiverId, String lineUserId, String name) {
        return CaregiverLineBinding.builder()
                .id(id).tenantId(1L).caregiverId(caregiverId)
                .lineUserId(lineUserId).lineDisplayName(name)
                .build();
    }
}
