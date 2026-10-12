package com.lifelog.travel.footprint;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lifelog.travel.footprint.FootSpot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface FootSpotMapper extends BaseMapper<FootSpot> {
    @Select("SELECT footprint_id, place_name, visit_type, visit_date, poi_reference, note, media_ids::text AS \"mediaIdsJson\", " +
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM footprint WHERE footprint_id = #{id}")
    FootSpot selectRecordById(@Param("id") Long id);

    @Update("UPDATE footprint SET location = CASE WHEN #{longitude} IS NULL OR #{latitude} IS NULL THEN NULL " +
            "ELSE ST_SetSRID(ST_MakePoint(#{longitude}, #{latitude}), 4326) END WHERE footprint_id = #{id}")
    int updateLocation(@Param("id") Long id, @Param("longitude") java.math.BigDecimal longitude,
                       @Param("latitude") java.math.BigDecimal latitude);

    @Select({"<script>", "SELECT footprint_id, place_name, visit_type, visit_date, poi_reference, note, media_ids::text AS \"mediaIdsJson\",",
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM footprint",
            "<if test='year != null'>WHERE visit_date &gt;= make_date(#{year},1,1) AND visit_date &lt; make_date(#{year}+1,1,1)</if>",
            "ORDER BY visit_date DESC NULLS LAST", "</script>"})
    List<FootSpot> selectRecords(@Param("year") Integer year);
}
