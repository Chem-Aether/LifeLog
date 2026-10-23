package com.lifelog.travel.trajectory;

import com.lifelog.common.api.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 提供 GPS 轨迹的管理和地图查询接口。 */
@RestController
@RequestMapping("/travel/trajectories")
@Tag(name = "轨迹管理接口")
public class TrajectoryController {
    @Autowired
    private TrajectoryService service;

    @GetMapping
    public Result list(@ModelAttribute TrajectoryQueryDTO query,
                       @RequestParam(required = false) Integer year, @RequestParam(required = false) Double west,
                       @RequestParam(required = false) Double south, @RequestParam(required = false) Double east,
                       @RequestParam(required = false) Double north, @RequestParam(required = false) Double zoom) {
        if (west != null || south != null || east != null || north != null || zoom != null) {
            return Result.success(service.findMapRoutes(west, south, east, north, zoom, year));
        }
        return Result.success(service.findPage(query));
    }

    @GetMapping("/{id}")
    public Result get(@PathVariable Long id) { return Result.success(service.findById(id)); }

    @PostMapping
    public Result add(@RequestBody Trajectory trajectory) { return Result.success(service.save(trajectory)); }

    @PostMapping("/batch")
    public Result addBatch(@RequestBody List<Trajectory> trajectories) { return Result.success(service.saveBatch(trajectories)); }

    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Trajectory trajectory) { return Result.success(service.update(id, trajectory)); }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) { service.delete(id); return Result.success(); }

    @DeleteMapping("/batch")
    public Result deleteBatch(@RequestBody List<Long> ids) { return Result.success(service.deleteBatch(ids)); }
}
