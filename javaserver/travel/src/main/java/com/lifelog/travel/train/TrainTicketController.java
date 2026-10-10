package com.lifelog.travel.train;

import com.lifelog.common.api.Result;
import com.lifelog.travel.train.TrainRecordQueryDTO;
import com.lifelog.travel.train.TrainRouteSampleRequest;
import com.lifelog.travel.train.TrainRecord;
import com.lifelog.travel.train.TrainTicketService;
import com.lifelog.travel.train.TrainRouteSamplingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 车票接口（对外分开）：
 * - 列表只返回车票+途径站数量
 * - 详情不展开途径站（站点详情走独立接口）
 * - 保存接口支持同时传入 stations，后端事务原子保存
 */
@RestController
@RequestMapping("/travel/trains")
@Tag(name = "火车票管理接口", description = "列表/详情/保存/删除")
public class TrainTicketController {

    @Autowired
    private TrainTicketService trainTicketService;
    @Autowired
    private TrainRouteSamplingService trainRouteSamplingService;

    @GetMapping
    public Result list(TrainRecordQueryDTO dto) {
        return Result.success(trainTicketService.list(dto));
    }

    @GetMapping("/{trainId}")
    public Result get(@PathVariable Long trainId) {
        TrainRecord ticket = trainTicketService.get(trainId);
        return Result.success(ticket);
    }

    @PostMapping
    public Result add(@RequestBody TrainRecord record) {
        return Result.success(trainTicketService.save(record));
    }

    @PutMapping("/{trainId}")
    public Result update(@PathVariable Long trainId, @RequestBody TrainRecord record) {
        return Result.success(trainTicketService.update(trainId, record));
    }

    @PostMapping("/route/sample")
    public Result sampleRoute(@RequestBody TrainRouteSampleRequest request) {
        return Result.success(trainRouteSamplingService.sample(request));
    }

    @DeleteMapping("/{trainId}")
    public Result delete(@PathVariable Long trainId) {
        trainTicketService.delete(trainId);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result deleteBatch(@RequestBody List<Long> ids) {
        return Result.success(trainTicketService.deleteBatch(ids));
    }
}
