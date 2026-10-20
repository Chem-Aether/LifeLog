package com.lifelog.bill.statistics;

import com.lifelog.common.api.Result;
import org.springframework.web.bind.annotation.*;

/** 提供账单概览、账户、时间线和分类统计接口。 */
@RestController
@RequestMapping("/bill/statistics")
public class BillStatisticsController {
    private final BillStatisticsService statisticsService;

    /**
     * 创建统计控制器。
     * @param statisticsService 账单统计业务服务
     */
    public BillStatisticsController(BillStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 查询指定时间范围的收支概览。
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @return 汇总统计数据
     */
    @GetMapping("/summary")
    public Result summary(@RequestParam(required = false) String startTime,
                          @RequestParam(required = false) String endTime) {
        return Result.success(statisticsService.summary(startTime, endTime));
    }

    /**
     * 查询指定时间范围内各账户的收支统计。
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @return 账户统计列表
     */
    @GetMapping("/accounts")
    public Result accounts(@RequestParam(required = false) String startTime,
                           @RequestParam(required = false) String endTime) {
        return Result.success(statisticsService.accountStatistics(startTime, endTime));
    }

    /**
     * 查询指定年份的日度和月度收支时间线。
     * @param year 统计年份
     * @return 日度及月度时间线
     */
    @GetMapping("/timeline")
    public Result timeline(@RequestParam int year) {
        return Result.success(statisticsService.timeline(year));
    }

    /**
     * 按分类汇总账单金额。
     * @param direction 收支方向
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @param detailed 是否细分分类
     * @param parentCategoryId 指定父分类 ID
     * @return 分类统计列表
     */
    @GetMapping("/categories")
    public Result categories(@RequestParam(required = false) String direction,
                             @RequestParam(required = false) String startTime,
                             @RequestParam(required = false) String endTime,
                             @RequestParam(defaultValue = "false") boolean detailed,
                             @RequestParam(required = false) Long parentCategoryId) {
        return Result.success(statisticsService.categoryStatistics(
                direction, startTime, endTime, detailed, parentCategoryId));
    }

    /** 查询支出商户偏好排行。 */
    @GetMapping("/merchants")
    public Result merchants(@RequestParam(required = false) String startTime,
                            @RequestParam(required = false) String endTime) {
        return Result.success(statisticsService.merchantPreferences(startTime, endTime));
    }

    /** 查询支出商品描述词频。 */
    @GetMapping("/products")
    public Result products(@RequestParam(required = false) String startTime,
                           @RequestParam(required = false) String endTime) {
        return Result.success(statisticsService.productPreferences(startTime, endTime));
    }
}
