package com.lifelog.travel.flight;

import lombok.Data;
import java.time.LocalDateTime;

/** 航班列表的筛选和分页参数。 */
@Data
public class FlightRecordQueryDTO {

    /** 页码。 */
    private Integer pageNum;
    /** 每页记录数。 */
    private Integer pageSize;

    /** 航班号筛选条件。 */
    private String flightNo;
    /** 出发机场 ICAO 筛选条件。 */
    private String departureIcao;
    /** 到达机场 ICAO 筛选条件。 */
    private String arrivalIcao;
    /** 起飞时间范围起点。 */
    private LocalDateTime departureTimeStart;
    /** 起飞时间范围终点。 */
    private LocalDateTime departureTimeEnd;
}
