package com.lifelog.bill.config;

import com.lifelog.common.api.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将账单数据完整性异常转换为统一错误响应。 */
@RestControllerAdvice
public class BillDataExceptionHandler {
    /**
     * 处理数据库约束或关联记录导致的数据写入失败。
     * @param exception 数据完整性异常
     * @return HTTP 400 错误响应
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result> handleDataIntegrity(DataIntegrityViolationException exception) {
        return ResponseEntity.badRequest().body(Result.error("数据被关联记录引用，或违反账单数据约束"));
    }
}
