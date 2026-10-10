package com.lifelog.travel.flight;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FlightRecordQueryDTO {

    // 分页
    private Integer pageNum;
    private Integer pageSize;

    // 查询条件
    private String flightNo;
    private String departureIcao;
    private String arrivalIcao;
    private LocalDateTime departureTimeStart;
    private LocalDateTime departureTimeEnd;
}
