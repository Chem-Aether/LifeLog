package com.lifelog.common.config;

import com.lifelog.common.api.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** 将常见请求异常转换为统一 API 响应。 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 将状态异常映射为对应 HTTP 状态码及错误消息。
     * @param exception 捕获到的状态异常
     * @return 统一错误响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Result> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "请求处理失败" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Result.error(message));
    }

    /**
     * 将无法解析的请求体转换为参数格式错误响应。
     * @param exception 请求体解析异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result> handleUnreadableMessage(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(Result.error("请求数据格式错误，请检查日期和数值字段"));
    }
}
