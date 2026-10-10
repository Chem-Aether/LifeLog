package com.lifelog.bill.statistics;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class BillStatisticsService {
    private final BillStatisticsMapper statisticsMapper;

    public BillStatisticsService(BillStatisticsMapper statisticsMapper) {
        this.statisticsMapper = statisticsMapper;
    }

    public Map<String, Object> summary(String start, String end) {
        return statisticsMapper.summary(timestamp(start), timestamp(end));
    }

    public List<Map<String, Object>> accountStatistics(String start, String end) {
        return statisticsMapper.accountStatistics(timestamp(start), timestamp(end));
    }

    public Map<String, Object> timeline(int year) {
        if (year < 1900 || year > 2200) throw new ResponseStatusException(BAD_REQUEST, "统计年份无效");
        Timestamp start = Timestamp.valueOf(LocalDate.of(year, 1, 1).atStartOfDay());
        Timestamp end = Timestamp.valueOf(LocalDate.of(year + 1, 1, 1).atStartOfDay());
        return Map.of("daily", statisticsMapper.dailyTimeline(start, end),
                "monthly", statisticsMapper.monthlyTimeline(start, end));
    }

    public List<Map<String, Object>> categoryStatistics(String direction, String start, String end,
                                                         boolean detailed, Long parentCategoryId) {
        if (direction != null && !direction.isBlank()
                && !List.of("income", "expense", "transfer").contains(direction))
            throw new ResponseStatusException(BAD_REQUEST, "账单方向无效");
        return statisticsMapper.categoryStatistics(blankToNull(direction), timestamp(start), timestamp(end),
                detailed, parentCategoryId);
    }

    private static Timestamp timestamp(String value) {
        return value == null || value.isBlank() ? null : Timestamp.valueOf(LocalDateTime.parse(value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
