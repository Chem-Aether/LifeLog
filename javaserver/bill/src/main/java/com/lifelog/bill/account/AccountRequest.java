package com.lifelog.bill.account;

import java.math.BigDecimal;

public record AccountRequest(String name, String code, BigDecimal openingBalance) {}
