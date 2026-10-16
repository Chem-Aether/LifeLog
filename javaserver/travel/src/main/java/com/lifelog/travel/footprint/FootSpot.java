package com.lifelog.travel.footprint;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.lifelog.travel.config.JsonbStringTypeHandler;
import lombok.Data;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 映射旅行足迹实体及其地理位置、媒体和行政区信息。 */
@Data
@TableName(value = "footprint", autoResultMap = true)
public class FootSpot {
    /** 足迹记录 ID。 */
    @TableId(value = "footprint_id", type = IdType.AUTO)
    private Long footprintId;
    /** 地点名称。 */
    private String placeName;
    /** 记录类型，例如旅行或途经。 */
    private String visitType;
    /** 到访日期。 */
    private LocalDate visitDate;
    /** 经度，非数据库列，由地理位置字段解析。 */
    @TableField(exist = false)
    private BigDecimal longitude;
    /** 纬度，非数据库列，由地理位置字段解析。 */
    @TableField(exist = false)
    private BigDecimal latitude;
    /** 关联的外部 POI 标识。 */
    private String poiReference;
    /** 足迹备注。 */
    private String note;
    /** 关联媒体 ID 列表。 */
    @TableField(exist = false)
    private List<String> mediaIds;
    /** 媒体 ID 列表的 JSONB 存储字段。 */
    @JsonIgnore
    @TableField(value = "media_ids", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String mediaIdsJson;
    /** 最细匹配行政区名称路径。 */
    @TableField(exist = false)
    private String regionName;
    /** 最细匹配行政区本国代码。 */
    @TableField(exist = false)
    private String regionCode;
    /** 匹配到的城市名称。 */
    @TableField(exist = false)
    private String cityName;
    /** 匹配到的省级行政区名称。 */
    @TableField(exist = false)
    private String provinceName;
    /** 最细匹配行政区 ID。 */
    @TableField(exist = false)
    private String regionId;
    /** 匹配到的城市行政区 ID。 */
    @TableField(exist = false)
    private String cityRegionId;
}
