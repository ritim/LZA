package com.lza.aethercare.common.error;

/** 業務例外：由 GlobalExceptionHandler 轉成 ProblemDetail。 */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final String detail;

    public BusinessException(ErrorCode code, String detail) {
        super(code.getMessage() + ": " + detail);
        this.code = code;
        this.detail = detail;
    }

    public BusinessException(ErrorCode code) {
        this(code, code.getMessage());
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }
}
