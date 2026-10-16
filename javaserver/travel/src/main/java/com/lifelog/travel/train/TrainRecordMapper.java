package com.lifelog.travel.train;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/** 提供列车行程及 PostGIS 线路几何的数据访问操作。 */
@Mapper
public interface TrainRecordMapper extends BaseMapper<TrainRecord> {
    /** 根据行程 ID 查询记录并读取线路 GeoJSON。 */
    @Select("SELECT train_id, train_no, train_type, train_model, start_station_name, departure_time, " +
            "end_station_name, arrival_time, origin_station_name, terminal_station_name, carriage_no, seat_no, seat_type, mileage_km, " +
            "ST_AsGeoJSON(route_geometry) AS route_geo_json, route_source, route_captured_at, note " +
            "FROM train_record WHERE train_id = #{id}")
    TrainRecord selectRecordById(@Param("id") Long id);

    /** 更新列车线路几何。 */
    @Update("UPDATE train_record SET route_geometry = CASE WHEN #{geoJson} IS NULL OR #{geoJson} = '' THEN NULL " +
            "ELSE ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(#{geoJson}), 4326)) END WHERE train_id = #{id}")
    int updateRoute(@Param("id") Long id, @Param("geoJson") String geoJson);

    /** 查询铁路行程，可按发车年份筛选。 */
    @Select({"<script>", "SELECT train_id, train_no, train_type, train_model, start_station_name, departure_time,",
            "end_station_name, arrival_time, origin_station_name, terminal_station_name, carriage_no, seat_no, seat_type, mileage_km,",
            "ST_AsGeoJSON(route_geometry) AS route_geo_json, route_source, route_captured_at, note FROM train_record",
            "<if test='year != null'>WHERE departure_time &gt;= make_date(#{year},1,1) AND departure_time &lt; make_date(#{year}+1,1,1)</if>",
            "ORDER BY departure_time DESC", "</script>"})
    List<TrainRecord> selectRecords(@Param("year") Integer year);
}
