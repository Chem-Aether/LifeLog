package com.lifelog.bill.statistics;

import com.lifelog.common.api.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bill/statistics")
public class BillStatisticsController {
    private final BillStatisticsService statisticsService;

    public BillStatisticsController(BillStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/summary")
    public Result summary(@RequestParam(required = false) String startTime,
                          @RequestParam(required = false) String endTime) {
        return Result.success(statisticsService.summary(startTime, endTime));
    }

    @GetMapping("/accounts")
    public Result accounts(@RequestParam(required = false) String startTime,
                           @RequestParam(required = false) String endTime) {
        return Result.success(statisticsService.accountStatistics(startTime, endTime));
    }

    @GetMapping("/timeline")
    public Result timeline(@RequestParam int year) {
        return Result.success(statisticsService.timeline(year));
    }

    @GetMapping("/categories")
    public Result categories(@RequestParam(required = false) String direction,
                             @RequestParam(required = false) String startTime,
                             @RequestParam(required = false) String endTime,
                             @RequestParam(defaultValue = "false") boolean detailed,
                             @RequestParam(required = false) Long parentCategoryId) {
        return Result.success(statisticsService.categoryStatistics(
                direction, startTime, endTime, detailed, parentCategoryId));
    }
}
