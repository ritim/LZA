package com.lza.aethercare.notification.line;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lza.aethercare.action.dto.CreateCareActionRequest;
import com.lza.aethercare.action.enums.CareActionType;
import com.lza.aethercare.action.service.CareActionService;
import com.lza.aethercare.common.error.BusinessException;
import com.lza.aethercare.common.error.ErrorCode;
import com.lza.aethercare.notification.line.entity.CaregiverLineBinding;
import com.lza.aethercare.notification.line.repository.CaregiverLineBindingRepository;
import com.lza.aethercare.notification.line.service.LineBindingService;
import com.lza.aethercare.task.entity.CareTask;
import com.lza.aethercare.task.service.CareTaskService;
import com.lza.aethercare.tenant.context.TenantContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LINE webhook postback「我收到了」路徑單元測試。
 *
 * <p>覆蓋三條重要路徑：
 * <ol>
 *   <li>已綁定 + ACKNOWLEDGE 成功 → 呼叫 CareActionService.handle、reply 成功訊息</li>
 *   <li>未綁定 → 拒絕並回提示，不打 CareActionService</li>
 *   <li>TASK_ALREADY_FINALIZED → 回友善訊息（不是 500）</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class LineWebhookControllerTest {

    private static final String SECRET = "test-channel-secret";
    private static final String LINE_USER = "Uabcdef1234567890";
    private static final Long TENANT_ID = 7L;
    private static final Long CAREGIVER_ID = 42L;

    @Mock LineBindingService bindingService;
    @Mock ObjectProvider<LineMessagingClient> lineClientProvider;
    @Mock LineMessagingClient lineClient;
    @Mock CaregiverLineBindingRepository bindingRepo;
    @Mock CareActionService careActionService;
    @Mock CareTaskService careTaskService;

    LineWebhookController controller;

    @BeforeEach
    void setUp() {
        LineProperties props = new LineProperties(true, SECRET, "token", List.of());
        controller = new LineWebhookController(
                props, new ObjectMapper(), bindingService,
                lineClientProvider, bindingRepo, careActionService, careTaskService);
        ReflectionTestUtils.setField(controller, "webBaseUrl", "http://test.local");
        when(lineClientProvider.getIfAvailable()).thenReturn(lineClient);
    }

    @AfterEach
    void clearTenant() {
        // 確保即使測試失敗，後續測試 ThreadLocal 也乾淨
        TenantContext.clear();
    }

    @Test
    void postback_ack_with_binding_should_invoke_acknowledge_and_reply_success() {
        givenBindingExists();
        // task → eventId 用於 reply 中的 dashboard deep link
        given(careTaskService.findById(555L)).willReturn(Optional.of(
                CareTask.builder().id(555L).eventId(8888L).build()));

        ResponseEntity<Void> resp = postWebhook(postbackBody("ack:555"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        ArgumentCaptor<CreateCareActionRequest> reqCap = ArgumentCaptor.forClass(CreateCareActionRequest.class);
        then(careActionService).should().handle(eq(555L), eq(CAREGIVER_ID), reqCap.capture());
        assertThat(reqCap.getValue().getActionType()).isEqualTo(CareActionType.ACKNOWLEDGE);
        assertThat(reqCap.getValue().getNote()).contains(String.valueOf(CAREGIVER_ID));

        ArgumentCaptor<String> replyCap = ArgumentCaptor.forClass(String.class);
        verify(lineClient).replyText(eq("rt-555"), replyCap.capture());
        String reply = replyCap.getValue();
        assertThat(reply).contains("已記錄您收到任務 #555");
        // 必須警告「收到 != 已處理」，避免家屬誤以為流程已結
        assertThat(reply).contains("僅代表");
        // deep link 必須帶 event id 而非 dashboard fallback
        assertThat(reply).contains("http://test.local/caregiver/events/8888");

        // ThreadLocal 必須在離開 controller 後被清掉
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void postback_ack_without_binding_should_not_call_action_service() {
        given(bindingRepo.findByLineUserId(LINE_USER)).willReturn(Optional.empty());

        postWebhook(postbackBody("ack:777"));

        then(careActionService).should(never()).handle(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
        verify(lineClient).replyText(eq("rt-777"),
                org.mockito.ArgumentMatchers.contains("尚未綁定"));
    }

    @Test
    void postback_ack_with_finalized_task_should_reply_friendly_message() {
        givenBindingExists();
        willThrow(new BusinessException(ErrorCode.TASK_ALREADY_FINALIZED, "task=999"))
                .given(careActionService).handle(eq(999L), eq(CAREGIVER_ID), org.mockito.ArgumentMatchers.any());

        postWebhook(postbackBody("ack:999"));

        verify(lineClient).replyText(eq("rt-999"),
                org.mockito.ArgumentMatchers.contains("已被處理"));
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void postback_ack_with_non_numeric_task_id_should_reject() {
        // 不需 stub binding：taskId 解析失敗會在查 binding 前就 return
        postWebhook(postbackBody("ack:notanumber"));

        then(careActionService).should(never()).handle(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
        verify(lineClient).replyText(eq("rt-notanumber"),
                org.mockito.ArgumentMatchers.contains("無法解析任務編號"));
    }

    // ---------- helpers ----------

    private void givenBindingExists() {
        CaregiverLineBinding binding = CaregiverLineBinding.builder()
                .id(1L)
                .tenantId(TENANT_ID)
                .caregiverId(CAREGIVER_ID)
                .lineUserId(LINE_USER)
                .build();
        given(bindingRepo.findByLineUserId(LINE_USER)).willReturn(Optional.of(binding));
    }

    private static String postbackBody(String data) {
        String taskIdPart = data.startsWith("ack:") ? data.substring(4) : data;
        return """
            {"events":[{
              "type":"postback",
              "replyToken":"rt-%s",
              "source":{"userId":"%s"},
              "postback":{"data":"%s"}
            }]}
            """.formatted(taskIdPart, LINE_USER, data);
    }

    private ResponseEntity<Void> postWebhook(String body) {
        String sig = sign(body);
        return controller.receive(sig, body);
    }

    private static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
