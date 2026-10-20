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

    /** 不分页查询足迹记录。 */
    @GetMapping("/footprints")
    @Operation(summary = "查询全部足迹记录")
    public Result footprints(@ModelAttribute FootSpotQueryDTO query) {
        return Result.success(footSpotService.findAll(query));
    }

    /** 分页查询足迹；regionIds 由前端地图服务解析后提交。 */
    @PostMapping("/footprints/search")
    @Operation(summary = "分页查询足迹")
    public Result search(@RequestBody FootSpotQueryDTO query) {
        return Result.success(footSpotService.findPage(query));
    }

    /** 获取筛选条件下出现过的行政区编码。 */
    @GetMapping("/footprints/region-ids")
    @Operation(summary = "查询足迹涉及的行政区编码")
    public Result regionIds(@ModelAttribute FootSpotQueryDTO query) {
        return Result.success(footSpotService.findRegionIds(query));
    }

    /** 查询单条足迹详情。 */
    @GetMapping("/footprints/{id}")
    @Operation(summary = "查询单条足迹")
    public Result get(@PathVariable Long id) {
        return Result.success(footSpotService.findById(id));
    }

    /** 新增足迹记录。 */
    @PostMapping("/footprints")
    @Operation(summary = "新增足迹")
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
