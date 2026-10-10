package com.lifelog.bill.record;

import java.util.List;

public record ImportRecordsRequest(List<BillTransactionRequest> records) {}
