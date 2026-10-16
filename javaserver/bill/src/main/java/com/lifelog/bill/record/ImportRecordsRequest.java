package com.lifelog.bill.record;

import java.util.List;

/**
 * 批量导入账单记录的请求。
 * @param records 待导入的账单记录
 */
public record ImportRecordsRequest(List<BillTransactionRequest> records) {}
