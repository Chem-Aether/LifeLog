package com.lifelog.bill.record;

import com.lifelog.common.api.Result;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** 提供账单记录的查询、维护、导入和 CSV 导出接口。 */
@RestController
@RequestMapping("/bill/records")
public class BillRecordController {
    private final BillRecordService recordService;

    /**
     * 创建账单记录控制器。
     * @param recordService 账单记录业务服务
     */
    public BillRecordController(BillRecordService recordService) {
        this.recordService = recordService;
    }

    /**
     * 按筛选条件和分页参数查询账单记录。
     * @param direction 收支方向
     * @param accountId 账户 ID
     * @param categoryId 分类 ID
     * @param keyword 搜索关键字
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @param page 页码
     * @param pageSize 每页记录数
     * @param sortBy 排序字段
     * @param sortOrder 排序方向
     * @param excludeTransfer 是否排除转账
     * @return 分页账单记录
     */
    @GetMapping
    public Result records(@RequestParam(required = false) String direction,
                          @RequestParam(required = false) Long accountId,
                          @RequestParam(required = false) Long categoryId,
                          @RequestParam(required = false) String keyword,
                          @RequestParam(required = false) String startTime,
                          @RequestParam(required = false) String endTime,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "20") int pageSize,
                          @RequestParam(defaultValue = "occurredAt") String sortBy,
                          @RequestParam(defaultValue = "desc") String sortOrder,
                          @RequestParam(defaultValue = "false") boolean excludeTransfer) {
        return Result.success(recordService.page(direction, accountId, categoryId, keyword, startTime, endTime,
                page, pageSize, sortBy, sortOrder, excludeTransfer));
    }

    /**
     * 查询单条账单记录。
     * @param id 账单记录 ID
     * @return 账单详情
     */
    @GetMapping("/{id}")
    public Result record(@PathVariable long id) {
        return Result.success(recordService.getRecord(id));
    }

    /**
     * 创建账单记录。
     * @param request 账单记录请求
     * @return 新记录 ID
     */
    @PostMapping
    public Result create(@RequestBody BillTransactionRequest request) {
        return Result.success(recordService.create(request));
    }

    /**
     * 更新账单记录。
     * @param id 账单记录 ID
     * @param request 更新后的记录内容
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable long id, @RequestBody BillTransactionRequest request) {
        recordService.update(id, request);
        return Result.success();
    }

    /**
     * 删除账单记录。
     * @param id 账单记录 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable long id) {
        recordService.delete(id);
        return Result.success();
    }

    /**
     * 批量导入账单记录。
     * @param request 批量导入请求
     * @return 导入结果及成功数量
     */
    @PostMapping("/import")
    public Result importRecords(@RequestBody ImportRecordsRequest request) {
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "导入数据不能为空");
        return Result.success("导入完成", recordService.importRecords(request.records()));
    }

    /**
     * 根据筛选条件导出 UTF-8 CSV 文件。
     * @param direction 收支方向
     * @param accountId 账户 ID
     * @param categoryId 分类 ID
     * @param keyword 搜索关键字
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @return CSV 文件响应
     */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportRecords(@RequestParam(required = false) String direction,
                                                @RequestParam(required = false) Long accountId,
                                                @RequestParam(required = false) Long categoryId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String startTime,
                                                @RequestParam(required = false) String endTime) {
        byte[] csv = recordService.exportRecords(direction, accountId, categoryId, keyword, startTime, endTime);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=bill-records.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }
}
