package com.lifelog.bill.account;

import com.lifelog.common.api.Result;
import org.springframework.web.bind.annotation.*;

/** 提供账单账户及账户交易记录的 HTTP 接口。 */
@RestController
@RequestMapping("/bill/accounts")
public class BillAccountController {
    private final BillAccountService accountService;

    /**
     * 创建账户控制器。
     * @param accountService 账户业务服务
     */
    public BillAccountController(BillAccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 查询所有账户及其余额。
     * @return 账户列表
     */
    @GetMapping
    public Result accounts() {
        return Result.success(accountService.list());
    }

    /**
     * 创建账单账户。
     * @param request 账户信息
     * @return 新账户 ID
     */
    @PostMapping
    public Result create(@RequestBody AccountRequest request) {
        return Result.success(accountService.create(request));
    }

    /**
     * 更新账单账户。
     * @param id 账户 ID
     * @param request 更新后的账户信息
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable long id, @RequestBody AccountRequest request) {
        accountService.update(id, request);
        return Result.success();
    }

    /**
     * 删除账单账户。
     * @param id 账户 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable long id) {
        accountService.delete(id);
        return Result.success();
    }

    /**
     * 分页查询账户交易记录。
     * @param id 账户 ID
     * @param page 页码
     * @param pageSize 每页记录数
     * @param sortBy 排序字段
     * @param sortOrder 排序方向
     * @return 分页交易记录
     */
    @GetMapping("/{id}/records")
    public Result records(@PathVariable long id,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int pageSize,
                          @RequestParam(defaultValue = "occurredAt") String sortBy,
                          @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(accountService.records(id, page, pageSize, sortBy, sortOrder));
    }
}
