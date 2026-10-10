package com.lifelog.bill.account;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BillAccountService {
    private final BillAccountMapper accountMapper;

    public BillAccountService(BillAccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    public List<Map<String, Object>> list() {
        return accountMapper.findAll();
    }

    public long create(AccountRequest request) {
        validate(request);
        return accountMapper.insert(request.name().trim(), normalize(request.code()), balance(request));
    }

    public void update(long id, AccountRequest request) {
        validate(request);
        if (accountMapper.update(id, request.name().trim(), normalize(request.code()), balance(request)) == 0)
            throw new ResponseStatusException(NOT_FOUND, "账户不存在");
    }

    public void delete(long id) {
        if (accountMapper.delete(id) == 0) throw new ResponseStatusException(NOT_FOUND, "账户不存在");
    }

    public Map<String, Object> records(long accountId, int page, int pageSize, String sortBy, String sortOrder) {
        if (page < 1 || pageSize < 1 || pageSize > 100)
            throw new ResponseStatusException(BAD_REQUEST, "分页参数无效");
        if (sortOrder == null || !List.of("asc", "desc").contains(sortOrder.toLowerCase(Locale.ROOT)))
            throw new ResponseStatusException(BAD_REQUEST, "排序方向无效");
        if (accountMapper.countById(accountId) == 0) throw new ResponseStatusException(NOT_FOUND, "账户不存在");
        long offset = (long) (page - 1) * pageSize;
        List<Map<String, Object>> rows = accountMapper.findRecords(accountId, sortBy, sortOrder, pageSize, offset);
        return Map.of("records", rows, "total", accountMapper.countRecords(accountId), "page", page, "pageSize", pageSize);
    }

    private static void validate(AccountRequest request) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(BAD_REQUEST, "账户名称不能为空");
        if (request.openingBalance() != null && request.openingBalance().scale() > 2)
            throw new ResponseStatusException(BAD_REQUEST, "余额最多保留两位小数");
    }

    private static BigDecimal balance(AccountRequest request) {
        return request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
