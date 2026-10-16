package com.lifelog.travel.train;

import lombok.Data;

import java.time.LocalDateTime;

/** 铁路行程列表的筛选和分页参数。 */
@Data
public class TrainRecordQueryDTO {
    /** 页码。 */
    private Integer pageNum;
    /** 每页记录数。 */
    private Integer pageSize;

    /** 车次号筛选条件。 */
    private String trainNo;
    /** 出发站筛选条件。 */
    private String startStationName;
    /** 到达站筛选条件。 */
    private String endStationName;
    /** 发车时间范围起点。 */
    private LocalDateTime departureTimeStart;
    /** 发车时间范围终点。 */
    private LocalDateTime departureTimeEnd;
}
