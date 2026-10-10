package com.lifelog.bill.config;

import com.lifelog.common.api.Result;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BillDataExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result> handleDataIntegrity(DataIntegrityViolationException exception) {
        return ResponseEntity.badRequest().body(Result.error("数据被关联记录引用，或违反账单数据约束"));
    }
}
