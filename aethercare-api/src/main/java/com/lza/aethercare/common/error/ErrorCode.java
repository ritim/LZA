package com.lza.aethercare.common.error;

import org.springframework.http.HttpStatus;

/** 業務錯誤碼：對應 HTTP status 與預設訊息。 */
public enum ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "資源不存在"),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT, "狀態轉移不被允許"),
    TASK_ALREADY_FINALIZED(HttpStatus.CONFLICT, "此任務已被處理"),
    ESCALATION_NOT_AVAILABLE(HttpStatus.CONFLICT, "已無下一順位聯絡人"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "請求格式錯誤"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系統錯誤");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
