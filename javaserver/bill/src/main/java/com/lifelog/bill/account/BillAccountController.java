package com.lifelog.bill.account;

import com.lifelog.bill.account.AccountRequest;
import com.lifelog.common.api.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bill/accounts")
public class BillAccountController {
    private final BillAccountService accountService;

    public BillAccountController(BillAccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public Result accounts() {
        return Result.success(accountService.list());
    }

    @PostMapping
    public Result create(@RequestBody AccountRequest request) {
        return Result.success(accountService.create(request));
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable long id, @RequestBody AccountRequest request) {
        accountService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable long id) {
        accountService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/records")
    public Result records(@PathVariable long id,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int pageSize,
                          @RequestParam(defaultValue = "occurredAt") String sortBy,
                          @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(accountService.records(id, page, pageSize, sortBy, sortOrder));
    }
}
