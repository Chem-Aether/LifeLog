package com.lifelog.travel.config;

import com.lifelog.common.api.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将旅行接口的非法参数异常转换为统一错误响应。 */
@RestControllerAdvice
public class TravelInputExceptionHandler {
    /**
     * 以 HTTP 400 返回参数校验错误。
     * @param exception 非法参数异常
     * @return 统一错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Result.error(exception.getMessage()));
    }
}
