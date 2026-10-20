package com.lifelog.travel.trajectory;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 提供轨迹采样点的批量读写。 */
@Mapper
public interface TrajectoryPointMapper {
    @Select("SELECT sequence, segment_no AS \"segmentNo\", recorded_at AS \"recordedAt\", ST_X(location) AS longitude, ST_Y(location) AS latitude, ST_Z(location) AS \"altitudeM\", accuracy_m AS \"accuracyM\", speed_mps AS \"speedMps\" FROM trajectory_point WHERE trajectory_id = #{id} ORDER BY sequence")
    List<TrajectoryPoint> selectByTrajectoryId(@Param("id") Long id);

    @Delete("DELETE FROM trajectory_point WHERE trajectory_id = #{id}")
    int deleteByTrajectoryId(@Param("id") Long id);

    @Insert("<script>INSERT INTO trajectory_point (trajectory_id, sequence, segment_no, recorded_at, location, accuracy_m, speed_mps) VALUES <foreach collection='points' item='point' separator=','>(#{trajectoryId}, #{point.sequence}, #{point.segmentNo}, #{point.recordedAt}, ST_SetSRID(ST_MakePoint(#{point.longitude}, #{point.latitude}, #{point.altitudeM}),4326), #{point.accuracyM}, #{point.speedMps})</foreach></script>")
    int insertBatch(@Param("trajectoryId") Long trajectoryId, @Param("points") List<TrajectoryPoint> points);
}
