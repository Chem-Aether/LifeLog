package com.lifelog.bill.account;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** 处理账单账户的增删改查及关联交易分页查询。 */
@Service
public class BillAccountService {
    private final BillAccountMapper accountMapper;

    /**
     * 创建账户业务服务。
     * @param accountMapper 账户数据访问组件
     */
    public BillAccountService(BillAccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    /**
     * 查询账户及其余额。
     * @return 账户列表
     */
    public List<Map<String, Object>> list() {
        return accountMapper.findAll();
    }

    /**
     * 校验并创建账户。
     * @param request 账户信息
     * @return 新账户 ID
     */
    public long create(AccountRequest request) {
        validate(request);
        return accountMapper.insert(request.name().trim(), normalize(request.code()), balance(request));
    }

    /**
     * 校验并更新账户。
     * @param id 账户 ID
     * @param request 更新后的账户信息
     * @throws ResponseStatusException 账户不存在或请求无效时抛出
     */
    public void update(long id, AccountRequest request) {
        validate(request);
        if (accountMapper.update(id, request.name().trim(), normalize(request.code()), balance(request)) == 0)
            throw new ResponseStatusException(NOT_FOUND, "账户不存在");
    }

    /**
     * 删除指定账户。
     * @param id 账户 ID
     * @throws ResponseStatusException 账户不存在时抛出
     */
    public void delete(long id) {
        if (accountMapper.delete(id) == 0) throw new ResponseStatusException(NOT_FOUND, "账户不存在");
    }

    /**
     * 分页查询账户交易记录并返回总数。
     * @param accountId 账户 ID
     * @param page 页码
     * @param pageSize 每页条数
     * @param sortBy 排序字段
     * @param sortOrder 排序方向
     * @return 分页结果
     */
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

    /**
     * 校验账户请求中的名称和期初余额。
     * @param request 待校验的账户请求
     */
    private static void validate(AccountRequest request) {
        if (request == null || request.name() == null || request.name().isBlank())
            throw new ResponseStatusException(BAD_REQUEST, "账户名称不能为空");
        if (request.openingBalance() != null && request.openingBalance().scale() > 2)
            throw new ResponseStatusException(BAD_REQUEST, "余额最多保留两位小数");
    }

    /**
     * 获取账户期初余额，未填写时使用零。
     * @param request 账户请求
     * @return 期初余额
     */
    private static BigDecimal balance(AccountRequest request) {
        return request.openingBalance() == null ? BigDecimal.ZERO : request.openingBalance();
    }

    /**
     * 将空白账户代码规范化为 {@code null}。
     * @param value 原始账户代码
     * @return 规范化后的账户代码
     */
    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
