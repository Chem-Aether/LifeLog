package com.lifelog.travel.footprint;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("footprint")
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
    private String mediaId;
    @TableField(exist = false)
    private String regionName;
    @TableField(exist = false)
    private String regionCode;
}
