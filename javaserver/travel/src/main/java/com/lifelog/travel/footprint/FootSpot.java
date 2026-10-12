package com.lifelog.travel.footprint;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lifelog.travel.config.JsonbStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@TableName(value = "footprint", autoResultMap = true)
public class FootSpot {
    @TableId(value = "footprint_id", type = IdType.AUTO)
    private Long footprintId;
    private String placeName;
    private String visitType;
    private LocalDate visitDate;
    @TableField(exist = false)
    private BigDecimal longitude;
    @TableField(exist = false)
    private BigDecimal latitude;
    private String poiReference;
    private String note;
    @TableField(exist = false)
    private List<String> mediaIds;
    @JsonIgnore
    @TableField(value = "media_ids", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String mediaIdsJson;
    @TableField(exist = false)
    private String regionName;
    @TableField(exist = false)
    private String regionCode;
    @TableField(exist = false)
    private String cityName;
    @TableField(exist = false)
    private String provinceName;
}
