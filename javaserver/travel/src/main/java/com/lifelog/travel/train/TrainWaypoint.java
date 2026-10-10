package com.lifelog.travel.train;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("train_waypoint")
public class TrainWaypoint {
    @TableId(value = "waypoint_id", type = IdType.AUTO)
    private Long waypointId;
    private Long trainId;
    private Integer sequence;
    private String stationName;
    @TableField(exist = false)
    private BigDecimal longitude;
    @TableField(exist = false)
    private BigDecimal latitude;
    private LocalDateTime arrivalTime;
    private LocalDateTime departureTime;
}
