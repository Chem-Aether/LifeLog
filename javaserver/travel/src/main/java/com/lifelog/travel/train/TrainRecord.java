package com.lifelog.travel.train;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "train_record", autoResultMap = true)
public class TrainRecord {
    @TableId(value = "train_id", type = IdType.AUTO)
    private Long trainId;
    private String trainNo;
    private String trainType;
    private String trainModel;
    private String startStationName;
    private LocalDateTime departureTime;
    private String endStationName;
    private LocalDateTime arrivalTime;
    private String originStationName;
    private String terminalStationName;
    private String carriageNo;
    private String seatNo;
    private String seatType;
    private java.math.BigDecimal mileageKm;
    @TableField(exist = false)
    private List<TrainWaypoint> waypoints;
    @TableField(exist = false)
    private String routeGeoJson;
    private String routeSource;
    private LocalDateTime routeCapturedAt;
    private String note;
}
