package com.lifelog.travel.footprint;


import com.lifelog.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 提供旅行足迹的筛选、维护和批量删除接口。 */
@RestController
@RequestMapping("/travel")
@Tag(name = "足迹地点管理接口", description = "点亮足迹地点")
public class FootSpotController {
    @Autowired
    private FootSpotService footSpotService;

    /** 分页查询足迹，并支持地点、类型和行政区筛选。 */
    @GetMapping("/footprints")
    @Operation(summary = "分页查询足迹")
    public Result footprints(@RequestParam(required = false) String visitType,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) String region,
                             @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "10") int pageSize,
                             @RequestParam(defaultValue = "visitDate") String sortBy,
                             @RequestParam(defaultValue = "desc") String sortOrder) {
        return Result.success(footSpotService.findPage(visitType, keyword, region, page, pageSize, sortBy, sortOrder));
    }

    /** 查询足迹记录，可按年份筛选。 */
    @GetMapping("/footprints/all")
    @Operation(summary = "查询全部足迹")
    public Result allFootprints(@RequestParam(required = false) Integer year) {
        return Result.success(footSpotService.findAll(year));
    }

    /** 查询单条足迹详情。 */
    @GetMapping("/footprints/{id}")
    @Operation(summary = "查询单条足迹")
    public Result get(@PathVariable Long id) {
        return Result.success(footSpotService.findById(id));
    }

    /** 新增足迹记录。 */
    @PostMapping("/footprints")
    @Operation(summary = "新增足迹并点亮所属地区")
    public Result add(@RequestBody FootSpot spot) {
        return Result.success(footSpotService.add(spot));
    }

    /** 更新指定足迹记录。 */
    @PutMapping("/footprints/{id}")
    @Operation(summary = "修改足迹")
    public Result update(@PathVariable Long id, @RequestBody FootSpot spot) {
        return Result.success(footSpotService.update(id, spot));
    }

    /** 删除单条足迹记录。 */
    @DeleteMapping("/footprints/{id}")
    @Operation(summary = "删除足迹")
    public Result delete(@PathVariable Long id) {
        footSpotService.delete(id);
        return Result.success();
    }

    /** 批量删除足迹记录。 */
    @DeleteMapping("/footprints/batch")
    public Result deleteBatch(@RequestBody List<Long> ids) {
        return Result.success(footSpotService.deleteBatch(ids));
    }
}
