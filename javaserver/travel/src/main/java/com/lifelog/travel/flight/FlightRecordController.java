package com.lifelog.travel.flight;

import com.lifelog.common.api.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;


/** 提供航班记录的查询、创建、更新和删除接口。 */
@RestController
@RequestMapping("/travel/flights")
@Tag(name = "机票管理接口", description = "航班/火车/票务相关接口")
public class FlightRecordController {

    @Autowired
    private FlightRecordService flightRecordService;

    /**
     * 按航班号、机场及起飞时间条件查询航班。
     * @param dto 查询和分页参数
     * @return 航班分页结果
     */
    @GetMapping
    public Result list(FlightRecordQueryDTO dto) {
        if (dto == null) {
            dto = new FlightRecordQueryDTO();
        };
        return Result.success(flightRecordService.list(dto));
    }

    /** 按 ID 查询航班记录详情。 */
    @GetMapping("/{id}")
    public Result get(@PathVariable Long id) {
        return Result.success(flightRecordService.get(id));
    }

    /**
     * 创建航班记录。
     * @param flightRecord 航班记录数据
     * @return 创建后的航班记录
     */
    @PostMapping
    public Result save(@RequestBody FlightRecord flightRecord) {
        return Result.success(flightRecordService.save(flightRecord));
    }

    /**
     * 更新航班记录。
     * @param id 航班记录 ID
     * @param flightRecord 更新数据
     * @return 更新后的航班记录
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody FlightRecord flightRecord) {
        return Result.success(flightRecordService.update(id, flightRecord));
    }

    // 删除
    /**
     * 删除单条航班记录。
     * @param id 航班记录 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result remove(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return Result.error("ID不能为空");
        }
        flightRecordService.delete(id);
        return Result.success("删除成功");
    }

    /**
     * 批量删除航班记录。
     * @param ids 航班记录 ID 列表
     * @return 删除记录数
     */
    @DeleteMapping("/batch")
    public Result removeBatch(@RequestBody List<Long> ids) {
        return Result.success(flightRecordService.deleteBatch(ids));
    }
}
