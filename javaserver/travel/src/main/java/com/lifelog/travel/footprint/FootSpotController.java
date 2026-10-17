package com.lifelog.travel.footprint;


import com.lifelog.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/** 提供旅行足迹的筛选、维护和批量删除接口。 */
@RestController
@RequestMapping("/travel")
@Tag(name = "足迹地点管理接口", description = "点亮足迹地点")
public class FootSpotController {
    @Autowired
    private FootSpotService footSpotService;

    /** 查询足迹；不传分页参数时返回全部记录，传入完整分页参数时返回分页结果。 */
    @GetMapping("/footprints")
    @Operation(summary = "查询足迹", description = "page 与 pageSize 均不传时返回全部记录；同时传入时分页")
    public Result footprints(@ModelAttribute FootSpotQueryDTO query) {
        if (query.getPage() == null && query.getPageSize() == null) {
            return Result.success(footSpotService.findAll(query));
        }
        if (query.getPage() == null || query.getPageSize() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page 和 pageSize 必须同时提供");
        }
        return Result.success(footSpotService.findPage(query));
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
