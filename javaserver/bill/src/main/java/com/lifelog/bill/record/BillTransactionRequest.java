package com.lifelog.bill.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建、更新账单记录的请求。
 * @param occurredAt 交易时间
 * @param amount 交易金额
 * @param fromAccountId 付款账户 ID
 * @param toAccountId 收款账户 ID
 * @param counterparty 交易对象
 * @param description 交易摘要
 * @param categoryId 分类 ID
 * @param remark 备注
 */
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
