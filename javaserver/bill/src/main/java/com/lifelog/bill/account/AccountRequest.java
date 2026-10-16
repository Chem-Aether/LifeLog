package com.lifelog.bill.account;

import java.math.BigDecimal;

/**
 * 新建或更新账单账户的请求。
 * @param name 账户名称
 * @param code 账户代码
 * @param openingBalance 期初余额
 */
public record AccountRequest(String name, String code, BigDecimal openingBalance) {}
