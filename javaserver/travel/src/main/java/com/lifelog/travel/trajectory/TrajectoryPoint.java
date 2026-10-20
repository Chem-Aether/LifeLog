package com.lifelog.travel.trajectory;

import lombok.Data;

import java.time.OffsetDateTime;

/** 一次轨迹中的 GPS 采样点。 */
@Data
public class TrajectoryPoint {
    private Integer sequence;
    private Integer segmentNo;
    private OffsetDateTime recordedAt;
    private Double longitude;
    private Double latitude;
    private Double altitudeM;
    private Double accuracyM;
    private Double speedMps;
}
