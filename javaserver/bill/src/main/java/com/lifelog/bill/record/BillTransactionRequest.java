package com.lifelog.bill.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillTransactionRequest(
        LocalDateTime occurredAt,
        BigDecimal amount,
        Long fromAccountId,
        Long toAccountId,
        String counterparty,
        String description,
        Long categoryId,
        String remark
) {}
