package com.lifelog.travel.flight;

import com.lifelog.common.api.Result;
import com.lifelog.travel.flight.FlightRecordQueryDTO;
import com.lifelog.travel.flight.FlightRecord;
import com.lifelog.travel.flight.FlightRecordService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;


@RestController
@RequestMapping("/travel/flights")
@Tag(name = "机票管理接口", description = "航班/火车/票务相关接口")
public class FlightRecordController {

    @Autowired
    private FlightRecordService flightRecordService;

    // 条件查询
    @GetMapping
    public Result list(FlightRecordQueryDTO dto) {
        if (dto == null) dto = new FlightRecordQueryDTO();
        return Result.success(flightRecordService.list(dto));
    }

    // 单个新增
    @PostMapping
    public Result save(@RequestBody FlightRecord flightRecord) {
        return Result.success(flightRecordService.save(flightRecord));
    }

    // 修改
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody FlightRecord flightRecord) {
        return Result.success(flightRecordService.update(id, flightRecord));
    }

    // 删除
    @DeleteMapping("/{id}")
    public Result remove(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Result.error("ID不能为空");
        }
        flightRecordService.delete(id);
        return Result.success("删除成功");
    }

    @DeleteMapping("/batch")
    public Result removeBatch(@RequestBody List<Long> ids) {
        return Result.success(flightRecordService.deleteBatch(ids));
    }
}
