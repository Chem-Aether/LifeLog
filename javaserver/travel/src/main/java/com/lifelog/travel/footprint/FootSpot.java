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
    /** 坐标反查得到的最细行政区节点 ID 缓存。 */
    private String regionId;
    /** 经度，非数据库列，由地理位置字段解析。 */
    @TableField(exist = false)
    private BigDecimal longitude;
    /** 纬度，非数据库列，由地理位置字段解析。 */
    @TableField(exist = false)
    private BigDecimal latitude;
    /** 地点类型，例如景点、餐厅或车站。 */
    private String locationType;
    /** 足迹备注。 */
    private String note;
    /** 关联媒体 URL 列表。 */
    @TableField(exist = false)
    private List<String> mediaUrls;
    /** 媒体 URL 列表的 JSONB 存储字段。 */
    @JsonIgnore
    @TableField(value = "media_urls", typeHandler = JsonbStringTypeHandler.class, jdbcType = JdbcType.OTHER)
    private String mediaUrlsJson;
}
