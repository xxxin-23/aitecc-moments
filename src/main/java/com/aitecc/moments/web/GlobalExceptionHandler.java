package com.aitecc.moments.web;

import com.aitecc.moments.service.BizException;
import java.time.LocalDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** 统一错误响应：前端只需要读 {@code error} 字段，不用猜 HTTP body 的形状。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBiz(BizException e) {
        return ResponseEntity.status(e.getStatus()).body(body(e.getStatus(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String message =
                e.getBindingResult().getFieldErrors().stream()
                        .map(f -> f.getField() + " " + f.getDefaultMessage())
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("参数不合法");
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, message));
    }

    /**
     * 缺令牌是「没登录」而不是服务器故障 —— 前端拿到 401 才能正确地弹出登录框。
     * 其他缺失的 header（如果有）仍然按 400 处理。
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException e) {
        boolean isToken = "X-Auth-Token".equalsIgnoreCase(e.getHeaderName());
        return ResponseEntity.status(isToken ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST)
                .body(body(isToken ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST,
                        isToken ? "请先登录" : "缺少请求头：" + e.getHeaderName()));
    }

    /** 路径里的 id 是字母、文件字段名写错之类 —— 都是调用方的锅，回 400 别当成服务器故障。 */
    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<Map<String, Object>> handleMismatch(Exception e) {
        String message =
                e instanceof MethodArgumentTypeMismatchException
                        ? "参数格式不对：" + ((MethodArgumentTypeMismatchException) e).getName()
                        : "请求格式不支持";
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, message));
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MissingServletRequestPartException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, "请求参数不完整"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
        // 详细堆栈只进日志，不回给客户端（避免泄露内部路径等）
        log.error("未处理异常", e);
        return ResponseEntity.internalServerError()
                .body(body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器开小差了，稍后再试"));
    }

    private Map<String, Object> body(HttpStatus status, String message) {
        return Map.of(
                "error", message,
                "status", status.value(),
                "timestamp", LocalDateTime.now().toString());
    }
}
