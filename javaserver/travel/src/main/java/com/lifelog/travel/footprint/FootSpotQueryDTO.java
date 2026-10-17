package com.lifelog.travel.footprint;

import lombok.Data;

/** 足迹查询的筛选、分页和排序参数。 */
@Data
public class FootSpotQueryDTO {
    /** 到访年份。 */
    private Integer year;
    /** 足迹性质筛选，可选值为 travel 或 transit。 */
    private String visitType;
    /** 地点名称关键字。 */
    private String keyword;
    /** 行政区名称关键字。 */
    private String region;
    /** 页码。 */
    private Integer page;
    /** 每页记录数。 */
    private Integer pageSize;
    /** 排序字段。 */
    private String sortBy = "visitDate";
    /** 排序方向。 */
    private String sortOrder = "desc";
}
