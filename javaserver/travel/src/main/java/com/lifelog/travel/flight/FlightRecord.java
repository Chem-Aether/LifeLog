package com.lifelog.travel.flight;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lifelog.travel.config.JsonbStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "flight_record", autoResultMap = true)
public class FlightRecord {
    @TableId(value = "flight_id", type = IdType.AUTO)
    private Long flightId;
    private String flightNo;
    private String airline;
    private String aircraftType;
    private String aircraftRegistration;
    private String departureIcao;
    private String departureAirportName;
    private String departureTerminal;
    private String boardingMethod;
    private LocalDateTime departureTime;
    private String arrivalIcao;
    private String arrivalAirportName;
    private String arrivalTerminal;
    private String deboardingMethod;
    private LocalDateTime arrivalTime;
    @TableField(exist = false)
    private List<Map<String, Object>> stopovers;
    @JsonIgnore
    @TableField(value = "stopovers", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String stopoversJson;
    private String seatNo;
    private BigDecimal distanceKm;
    private String note;
}
