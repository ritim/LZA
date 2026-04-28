package com.lza.aethercare.common.error;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** 全域例外處理：將業務 / 驗證 / 未預期例外轉成 ProblemDetail。 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 業務例外：依 ErrorCode 對應 HTTP status。 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getCode();
        ProblemDetail problem = ProblemDetail.forStatus(code.getStatus());
        problem.setTitle(code.getMessage());
        problem.setDetail(ex.getDetail());
        problem.setProperty("code", code.name());
        return ResponseEntity.status(code.getStatus()).body(problem);
    }

    /** Bean Validation 失敗：列出每個欄位錯誤。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ErrorCode.INVALID_REQUEST.getMessage());
        problem.setDetail("請求欄位驗證失敗");
        problem.setProperty("code", ErrorCode.INVALID_REQUEST.name());
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** PathVariable / RequestParam 驗證失敗（@Validated）。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ErrorCode.INVALID_REQUEST.getMessage());
        problem.setDetail("請求參數驗證失敗");
        problem.setProperty("code", ErrorCode.INVALID_REQUEST.name());
        Map<String, String> violations = new LinkedHashMap<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            violations.put(v.getPropertyPath().toString(), v.getMessage());
        }
        problem.setProperty("violations", violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** 請求 body 無法解析成 JSON。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleNotReadable(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(ErrorCode.INVALID_REQUEST.getMessage());
        problem.setDetail("請求 JSON 格式錯誤");
        problem.setProperty("code", ErrorCode.INVALID_REQUEST.name());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** 未預期例外：500 fallback，不暴露 stack trace。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknown(Exception ex) {
        log.error("未預期例外", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle(ErrorCode.INTERNAL_ERROR.getMessage());
        problem.setDetail("系統發生未預期錯誤，請稍後再試");
        problem.setProperty("code", ErrorCode.INTERNAL_ERROR.name());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
