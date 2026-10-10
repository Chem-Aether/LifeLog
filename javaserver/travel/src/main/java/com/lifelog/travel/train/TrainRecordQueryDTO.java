package com.lifelog.travel.train;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainRecordQueryDTO {
    private Integer pageNum;
    private Integer pageSize;

    private String trainNo;
    private String startStationName;
    private String endStationName;
    private LocalDateTime departureTimeStart;
    private LocalDateTime departureTimeEnd;
}
