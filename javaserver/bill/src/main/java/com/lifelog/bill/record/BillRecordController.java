package com.lifelog.bill.record;

import com.lifelog.common.api.Result;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/bill/records")
public class BillRecordController {
    private final BillRecordService recordService;

    public BillRecordController(BillRecordService recordService) {
        this.recordService = recordService;
    }

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

    @GetMapping("/{id}")
    public Result record(@PathVariable long id) {
        return Result.success(recordService.getRecord(id));
    }

    @PostMapping
    public Result create(@RequestBody BillTransactionRequest request) {
        return Result.success(recordService.create(request));
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable long id, @RequestBody BillTransactionRequest request) {
        recordService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable long id) {
        recordService.delete(id);
        return Result.success();
    }

    @PostMapping("/import")
    public Result importRecords(@RequestBody ImportRecordsRequest request) {
        if (request == null) throw new ResponseStatusException(BAD_REQUEST, "导入数据不能为空");
        return Result.success("导入完成", recordService.importRecords(request.records()));
    }

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
