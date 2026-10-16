package com.lifelog.bill.statistics;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** 处理账单统计参数校验、时间范围转换和统计查询。 */
@Service
public class BillStatisticsService {
    private final BillStatisticsMapper statisticsMapper;

    /**
     * 创建统计业务服务。
     * @param statisticsMapper 统计数据访问组件
     */
    public BillStatisticsService(BillStatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    /**
     * 汇总指定时间范围的收支金额。
     * @param start 起始时间文本
     * @param end 截止时间文本
     * @return 收支概览
     */
    public Map<String, Object> summary(String start, String end) {
        return statisticsMapper.summary(timestamp(start), timestamp(end));
    }

    /**
     * 查询指定时间范围内的账户收支。
     * @param start 起始时间文本
     * @param end 截止时间文本
     * @return 账户统计列表
     */
    public List<Map<String, Object>> accountStatistics(String start, String end) {
        return statisticsMapper.accountStatistics(timestamp(start), timestamp(end));
    }

    /**
     * 查询指定年份的每日和每月收支时间线。
     * @param year 统计年份
     * @return 双粒度时间线
     * @throws ResponseStatusException 年份不在允许范围内时抛出
     */
    public Map<String, Object> timeline(int year) {
        if (year < 1900 || year > 2200) throw new ResponseStatusException(BAD_REQUEST, "统计年份无效");
        Timestamp start = Timestamp.valueOf(LocalDate.of(year, 1, 1).atStartOfDay());
        Timestamp end = Timestamp.valueOf(LocalDate.of(year + 1, 1, 1).atStartOfDay());
        return Map.of("daily", statisticsMapper.dailyTimeline(start, end),
                "monthly", statisticsMapper.monthlyTimeline(start, end));
    }

    /**
     * 按分类统计指定方向和时间范围内的金额。
     * @param direction 收支方向
     * @param start 起始时间文本
     * @param end 截止时间文本
     * @param detailed 是否显示细分类
     * @param parentCategoryId 父分类 ID
     * @return 分类统计列表
     * @throws ResponseStatusException 收支方向无效时抛出
     */
    public List<Map<String, Object>> categoryStatistics(String direction, String start, String end,
                                                         boolean detailed, Long parentCategoryId) {
        if (direction != null && !direction.isBlank()
                && !List.of("income", "expense", "transfer").contains(direction))
            throw new ResponseStatusException(BAD_REQUEST, "账单方向无效");
        return statisticsMapper.categoryStatistics(blankToNull(direction), timestamp(start), timestamp(end),
                detailed, parentCategoryId);
    }

    /**
     * 将 ISO 本地日期时间转换为 SQL 时间戳。
     * @param value 日期时间文本
     * @return SQL 时间戳；未提供时返回 {@code null}
     */
    private static Timestamp timestamp(String value) {
        return value == null || value.isBlank() ? null : Timestamp.valueOf(LocalDateTime.parse(value));
    }

    /**
     * 将空白字符串规范化为 {@code null}。
     * @param value 原始字符串
     * @return 规范化后的字符串
     */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
