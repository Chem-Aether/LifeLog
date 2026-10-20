package com.lifelog.travel.trajectory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/** 一次 GPS 轨迹记录及其展示摘要。 */
@Data
@TableName("trajectory")
public class Trajectory {
    @TableId(value = "trajectory_id", type = IdType.AUTO)
    private Long trajectoryId;
    private String name;
    private String activityType;
    private OffsetDateTime startedAt;
    private OffsetDateTime endedAt;
    private Double distanceM;
    private Integer durationS;
    private Double elevationGainM;
    private Double elevationLossM;
    private Integer pointCount;
    private String note;
    @TableField(exist = false)
    private String routeGeoJson;
    @TableField(exist = false)
    private List<TrajectoryPoint> points;
}
