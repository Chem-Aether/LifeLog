package com.lifelog.travel.train;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifelog.travel.train.TrainWaypoint;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TrainWaypointMapper extends BaseMapper<TrainWaypoint> {
    @Select("SELECT waypoint_id, train_id, sequence, station_name, arrival_time, departure_time, " +
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM train_waypoint " +
            "WHERE train_id = #{trainId} ORDER BY sequence")
    List<TrainWaypoint> selectByTrainId(@Param("trainId") Long trainId);

    @Select("SELECT waypoint_id, train_id, sequence, station_name, arrival_time, departure_time, " +
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM train_waypoint ORDER BY train_id, sequence")
    List<TrainWaypoint> selectAllRecords();

    @Insert("INSERT INTO train_waypoint (train_id, sequence, station_name, arrival_time, departure_time, location) " +
            "VALUES (#{trainId}, #{sequence}, #{stationName}, #{arrivalTime}, #{departureTime}, " +
            "CASE WHEN #{longitude} IS NULL OR #{latitude} IS NULL THEN NULL " +
            "ELSE ST_SetSRID(ST_MakePoint(#{longitude}, #{latitude}),4326) END)")
    int insertRecord(TrainWaypoint waypoint);

    @Delete("DELETE FROM train_waypoint WHERE train_id = #{trainId}")
    int deleteByTrainId(@Param("trainId") Long trainId);
}
