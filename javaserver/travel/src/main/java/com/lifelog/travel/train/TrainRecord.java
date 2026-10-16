package com.lifelog.travel.train;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 映射铁路行程、线路轨迹及行程属性。 */
@Data
@TableName(value = "train_record", autoResultMap = true)
public class TrainRecord {
    /** 行程 ID。 */
@TableId(value = "train_id", type = IdType.AUTO)
    private Long trainId;
    /** 车次号。 */
    private String trainNo;
    /** 铁路类型，例如高速铁路或普速铁路。 */
    private String trainType;
    /** 列车型号。 */
    private String trainModel;
    /** 乘车记录中的出发站名称。 */
    private String startStationName;
    /** 发车时间。 */
    private LocalDateTime departureTime;
    /** 乘车记录中的到达站名称。 */
    private String endStationName;
    /** 到达时间。 */
    private LocalDateTime arrivalTime;
    /** 列车始发站名称。 */
    private String originStationName;
    /** 列车终到站名称。 */
    private String terminalStationName;
    /** 车厢号。 */
    private String carriageNo;
    /** 座位号。 */
    private String seatNo;
    /** 席别。 */
    private String seatType;
    /** 行程里程，单位为千米。 */
    private java.math.BigDecimal mileageKm;
    /** 有序途经站及其时间、坐标。 */
    @TableField(exist = false)
    private List<TrainWaypoint> waypoints;
    /** 铁路线路 GeoJSON 几何。 */
    @TableField(exist = false)
    private String routeGeoJson;
    /** 线路数据来源。 */
    private String routeSource;
    /** 线路采集时间。 */
    private LocalDateTime routeCapturedAt;
    /** 行程备注。 */
    private String note;
}
