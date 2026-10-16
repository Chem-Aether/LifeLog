package com.lifelog.travel.train;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 定义铁路途经站的查询、插入和替换操作。 */
@Mapper
public interface TrainWaypointMapper extends BaseMapper<TrainWaypoint> {
    /** 按行程 ID 和站序查询途经站。 */
    @Select("SELECT waypoint_id, train_id, sequence, station_name, arrival_time, departure_time, " +
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM train_waypoint " +
            "WHERE train_id = #{trainId} ORDER BY sequence")
    List<TrainWaypoint> selectByTrainId(@Param("trainId") Long trainId);

    /** 查询全部列车途经站并按行程和站序排列。 */
    @Select("SELECT waypoint_id, train_id, sequence, station_name, arrival_time, departure_time, " +
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM train_waypoint ORDER BY train_id, sequence")
    List<TrainWaypoint> selectAllRecords();

    /** 插入途经站及其空间坐标。 */
    @Insert("INSERT INTO train_waypoint (train_id, sequence, station_name, arrival_time, departure_time, location) " +
            "VALUES (#{trainId}, #{sequence}, #{stationName}, #{arrivalTime}, #{departureTime}, " +
            "CASE WHEN #{longitude} IS NULL OR #{latitude} IS NULL THEN NULL " +
            "ELSE ST_SetSRID(ST_MakePoint(#{longitude}, #{latitude}),4326) END)")
    int insertRecord(TrainWaypoint waypoint);

    /** 删除指定行程的全部途经站。 */
    @Delete("DELETE FROM train_waypoint WHERE train_id = #{trainId}")
    int deleteByTrainId(@Param("trainId") Long trainId);
}
