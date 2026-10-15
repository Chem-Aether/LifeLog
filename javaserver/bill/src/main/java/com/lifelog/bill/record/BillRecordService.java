package com.lifelog.bill.record;

import com.lifelog.bill.account.BillAccountMapper;
import com.lifelog.bill.category.BillCategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BillRecordService {
    private final BillRecordMapper recordMapper;
    private final BillAccountMapper accountMapper;
    private final BillCategoryMapper categoryMapper;

    public BillRecordService(BillRecordMapper recordMapper, BillAccountMapper accountMapper, BillCategoryMapper categoryMapper) {
        this.recordMapper = recordMapper;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
    }

    public List<Map<String, Object>> list(String direction, Long accountId, Long categoryId,
                                          String keyword, String startTime, String endTime) {
        if (direction != null && !direction.isBlank()) requireDirection(direction);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        return recordMapper.findRecords(blankToNull(direction), accountId, categoryId, normalizedKeyword,
                timestamp(startTime), timestamp(endTime));
    }

    public Map<String, Object> page(String direction, Long accountId, Long categoryId, String keyword,
                                    String startTime, String endTime, int page, int pageSize,
                                    String sortBy, String sortOrder, boolean excludeTransfer) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new ResponseStatusException(BAD_REQUEST, "分页参数无效");
        if (direction != null && !direction.isBlank()) requireDirection(direction);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
        var start = timestamp(startTime);
        var end = timestamp(endTime);
        var order = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        var sort = List.of("amount", "description", "category", "direction", "occurredAt", "payer", "payee")
                .contains(sortBy) ? sortBy : "occurredAt";
        List<Map<String, Object>> records = recordMapper.findRecordsPage(blankToNull(direction), accountId, categoryId,
                normalizedKeyword, start, end, pageSize, (long) (page - 1) * pageSize, sort, order, excludeTransfer);
        long total = recordMapper.countRecords(blankToNull(direction), accountId, categoryId,
                normalizedKeyword, start, end, excludeTransfer);
        return Map.of("records", records, "total", total, "page", page, "pageSize", pageSize);
    }

    public Map<String, Object> getRecord(long id) {
        Map<String, Object> record = recordMapper.findRecordById(id);
        if (record == null) throw new ResponseStatusException(NOT_FOUND, "账单记录不存在");
        return record;
    }

    @Transactional
    public int importRecords(List<BillTransactionRequest> records) {
        if (records == null || records.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "导入文件没有账单记录");
        if (records.size() > 10000) throw new ResponseStatusException(BAD_REQUEST, "单次最多导入10000条记录");
        for (int i = 0; i < records.size(); i++) {
            try {
                BillTransactionRequest request = records.get(i);
                validate(request);
                checkAccounts(request);
                checkCategory(request.categoryId());
            } catch (ResponseStatusException e) {
                throw new ResponseStatusException(BAD_REQUEST, "第" + (i + 2) + "行：" + e.getReason());
            }
        }
        records.forEach(recordMapper::insert);
        return records.size();
    }

    public byte[] exportRecords(String direction, Long accountId, Long categoryId, String keyword,
                                String startTime, String endTime) {
        List<Map<String, Object>> rows = list(direction, accountId, categoryId, keyword, startTime, endTime);
        StringBuilder csv = new StringBuilder("\uFEFF交易时间,方向,金额,付款账户ID,付款账户,收款账户ID,收款账户,交易对象,分类ID,分类,摘要,备注\r\n");
        for (Map<String, Object> row : rows) {
            csv.append(csvValue(row.get("occurredAt"))).append(',')
                    .append(csvValue(row.get("direction"))).append(',')
                    .append(csvValue(row.get("amount"))).append(',')
                    .append(csvValue(row.get("fromAccountId"))).append(',')
                    .append(csvValue(row.get("fromAccountName"))).append(',')
                    .append(csvValue(row.get("toAccountId"))).append(',')
                    .append(csvValue(row.get("toAccountName"))).append(',')
                    .append(csvValue(row.get("counterparty"))).append(',')
                    .append(csvValue(row.get("categoryId"))).append(',')
                    .append(csvValue(row.get("category"))).append(',')
                    .append(csvValue(row.get("description"))).append(',')
                    .append(csvValue(row.get("remark"))).append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public long create(BillTransactionRequest request) {
        validate(request);
        checkAccounts(request);
        checkCategory(request.categoryId());
        return recordMapper.insertReturningId(request);
    }

    @Transactional
    public void update(long id, BillTransactionRequest request) {
        validate(request);
        checkAccounts(request);
        checkCategory(request.categoryId());
        if (recordMapper.update(id, request) == 0) throw new ResponseStatusException(NOT_FOUND, "账单记录不存在");
    }

    public void delete(long id) {
        if (recordMapper.delete(id) == 0) throw new ResponseStatusException(NOT_FOUND, "账单记录不存在");
    }

    private void checkAccounts(BillTransactionRequest request) {
        for (Long id : new Long[]{request.fromAccountId(), request.toAccountId()}) {
            if (id != null && accountMapper.countById(id) == 0)
                throw new ResponseStatusException(BAD_REQUEST, "所选账户不存在");
        }
    }

    private void checkCategory(Long id) {
        if (id != null && categoryMapper.countById(id) == 0)
            throw new ResponseStatusException(BAD_REQUEST, "所选分类不存在");
    }

    private static void validate(BillTransactionRequest request) {
        if (request == null || request.occurredAt() == null) throw new ResponseStatusException(BAD_REQUEST, "交易时间不能为空");
        if (request.amount() == null || request.amount().signum() <= 0 || request.amount().scale() > 2)
            throw new ResponseStatusException(BAD_REQUEST, "金额必须大于零且最多保留两位小数");
        if (request.fromAccountId() == null && request.toAccountId() == null)
            throw new ResponseStatusException(BAD_REQUEST, "出账账户和入账账户至少填写一个");
        if (request.fromAccountId() != null && request.fromAccountId().equals(request.toAccountId()))
            throw new ResponseStatusException(BAD_REQUEST, "出账与入账账户不能相同");
    }

    private static void requireDirection(String direction) {
        if (!List.of("income", "expense", "transfer").contains(direction))
            throw new ResponseStatusException(BAD_REQUEST, "账单方向无效");
    }

    private static Timestamp timestamp(String value) {
        return value == null || value.isBlank() ? null : Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String csvValue(Object value) {
        String text = value == null ? "" : value.toString();
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
