package com.lifelog.travel.train;

import lombok.Data;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 映射列车线路中的途经站点。 */
@Data
@TableName("train_waypoint")
public class TrainWaypoint {
    /** 途经站记录 ID。 */
@TableId(value = "waypoint_id", type = IdType.AUTO)
    private Long waypointId;
    /** 关联行程 ID。 */
    private Long trainId;
    /** 站点在线路中的顺序号。 */
    private Integer sequence;
    /** 站名。 */
    private String stationName;
    /** 站点经度。 */
@TableField(exist = false)
    private BigDecimal longitude;
    /** 站点纬度。 */
@TableField(exist = false)
    private BigDecimal latitude;
    /** 到站时间。 */
    private LocalDateTime arrivalTime;
    /** 发车时间。 */
    private LocalDateTime departureTime;
}
