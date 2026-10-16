package com.lifelog.travel.train;

import com.lifelog.common.api.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 提供铁路行程查询、维护和线路采样接口。 */
@RestController
@RequestMapping("/travel/trains")
@Tag(name = "火车票管理接口", description = "列表/详情/保存/删除")
public class TrainTicketController {

    @Autowired
    private TrainTicketService trainTicketService;
    @Autowired
    private TrainRouteSamplingService trainRouteSamplingService;

    /** 分页查询铁路行程。 */
    @GetMapping
    public Result list(TrainRecordQueryDTO dto) {
        return Result.success(trainTicketService.list(dto));
    }

    /** 查询铁路行程详情及途经站。 */
    @GetMapping("/{trainId}")
    public Result get(@PathVariable Long trainId) {
        TrainRecord ticket = trainTicketService.get(trainId);
        return Result.success(ticket);
    }

    /** 创建铁路行程。 */
    @PostMapping
    public Result add(@RequestBody TrainRecord record) {
        return Result.success(trainTicketService.save(record));
    }

    /** 更新铁路行程及其线路数据。 */
    @PutMapping("/{trainId}")
    public Result update(@PathVariable Long trainId, @RequestBody TrainRecord record) {
        return Result.success(trainTicketService.update(trainId, record));
    }

    /** 根据自动或手动途经站生成线路轨迹。 */
    @PostMapping("/route/sample")
    public Result sampleRoute(@RequestBody TrainRouteSampleRequest request) {
        return Result.success(trainRouteSamplingService.sample(request));
    }

    /** 删除单条铁路行程。 */
    @DeleteMapping("/{trainId}")
    public Result delete(@PathVariable Long trainId) {
        trainTicketService.delete(trainId);
        return Result.success();
    }

    /** 批量删除铁路行程。 */
    @DeleteMapping("/batch")
    public Result deleteBatch(@RequestBody List<Long> ids) {
        return Result.success(trainTicketService.deleteBatch(ids));
    }
}
