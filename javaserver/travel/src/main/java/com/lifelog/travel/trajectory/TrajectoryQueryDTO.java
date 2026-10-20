package com.lifelog.travel.trajectory;

import lombok.Data;

import java.time.LocalDate;

/** 轨迹列表的筛选、分页和排序参数。 */
@Data
public class TrajectoryQueryDTO {
    private Integer page = 1;
    private Integer pageSize = 10;
    private String keyword;
    private String activityType;
    private LocalDate date;
    private String sortBy = "startedAt";
    private String sortOrder = "desc";
}
