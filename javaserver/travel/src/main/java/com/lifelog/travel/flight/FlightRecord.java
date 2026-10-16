package com.lifelog.travel.flight;

import com.baomidou.mybatisplus.annotation.*;
import com.lifelog.travel.config.JsonbStopoversTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 映射航班记录及其经停机场 JSON 数据。 */
@Data
@TableName(value = "flight_record", autoResultMap = true)
public class FlightRecord {
    /** 航班记录 ID。 */
    @TableId(value = "flight_id", type = IdType.AUTO)
    private Long flightId;
    /** 航班号。 */
    private String flightNo;
    /** 承运航空公司。 */
    private String airline;
    /** 飞机型号。 */
    private String aircraftType;
    /** 飞机注册号。 */
    private String aircraftRegistration;
    /** 出发机场 ICAO 代码。 */
    private String departureIcao;
    /** 出发机场名称。 */
    private String departureAirportName;
    /** 出发航站楼。 */
    private String departureTerminal;
    /** 登机方式。 */
    private String boardingMethod;
    /** 起飞时间。 */
    private LocalDateTime departureTime;
    /** 到达机场 ICAO 代码。 */
    private String arrivalIcao;
    /** 到达机场名称。 */
    private String arrivalAirportName;
    /** 到达航站楼。 */
    private String arrivalTerminal;
    /** 下机方式。 */
    private String deboardingMethod;
    /** 到达时间。 */
    private LocalDateTime arrivalTime;
    /** 经停机场信息，以 JSONB 结构化存储。 */
    @TableField(value = "stopovers", typeHandler = JsonbStopoversTypeHandler.class, jdbcType = JdbcType.OTHER)
    private List<Map<String, Object>> stopovers;
    /** 座位号。 */
    private String seatNo;
    /** 舱位类型。 */
    private String cabinClass;
    /** 飞行距离，单位为千米。 */
    private BigDecimal distanceKm;
    /** 备注。 */
    private String note;
}
