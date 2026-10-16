package com.lifelog.travel.train;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 生成铁路线路轨迹的请求参数。 */
@Data
public class TrainRouteSampleRequest {
    /** 查询模式，支持自动查询或手动站点。 */
    private String mode;
    /** 待查询车次号。 */
    private String trainNo;
    /** 自动查询时使用的乘车日期。 */
    private LocalDate date;
    /** 手动模式下按线路顺序填写的站名。 */
    private List<String> stations;
}
