package com.lifelog.travel.train;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifelog.travel.train.TrainRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface  TrainRecordMapper  extends BaseMapper<TrainRecord> {
    @Select("SELECT train_id, train_no, train_type, train_model, start_station_name, departure_time, " +
            "end_station_name, arrival_time, origin_station_name, terminal_station_name, carriage_no, seat_no, seat_type, mileage_km, " +
            "ST_AsGeoJSON(route_geometry) AS route_geo_json, route_source, route_captured_at, note " +
            "FROM train_record WHERE train_id = #{id}")
    TrainRecord selectRecordById(@Param("id") Long id);

    @Update("UPDATE train_record SET route_geometry = CASE WHEN #{geoJson} IS NULL OR #{geoJson} = '' THEN NULL " +
            "ELSE ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(#{geoJson}), 4326)) END WHERE train_id = #{id}")
    int updateRoute(@Param("id") Long id, @Param("geoJson") String geoJson);

    @Select({"<script>", "SELECT train_id, train_no, train_type, train_model, start_station_name, departure_time,",
            "end_station_name, arrival_time, origin_station_name, terminal_station_name, carriage_no, seat_no, seat_type, mileage_km,",
            "ST_AsGeoJSON(route_geometry) AS route_geo_json, route_source, route_captured_at, note FROM train_record",
            "<if test='year != null'>WHERE departure_time &gt;= make_date(#{year},1,1) AND departure_time &lt; make_date(#{year}+1,1,1)</if>",
            "ORDER BY departure_time DESC", "</script>"})
    List<TrainRecord> selectRecords(@Param("year") Integer year);
}
