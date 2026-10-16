package com.lifelog.travel.footprint;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import java.util.Map;

/** 定义足迹记录及其地理坐标的数据库操作。 */
@Mapper
public interface FootSpotMapper extends BaseMapper<FootSpot> {
    /** 根据 ID 查询足迹并读取经纬度及 JSON 数据。 */
    @Select("SELECT footprint_id, place_name, visit_type, visit_date, poi_reference, note, media_ids::text AS \"mediaIdsJson\", " +
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM footprint WHERE footprint_id = #{id}")
    FootSpot selectRecordById(@Param("id") Long id);

    /** 更新足迹的 PostGIS 点位。 */
    @Update("UPDATE footprint SET location = CASE WHEN #{longitude} IS NULL OR #{latitude} IS NULL THEN NULL " +
            "ELSE ST_SetSRID(ST_MakePoint(#{longitude}, #{latitude}), 4326) END WHERE footprint_id = #{id}")
    int updateLocation(@Param("id") Long id, @Param("longitude") java.math.BigDecimal longitude,
                       @Param("latitude") java.math.BigDecimal latitude);

    /** 查询足迹记录，可按到访年份筛选。 */
    @Select({"<script>", "SELECT footprint_id, place_name, visit_type, visit_date, poi_reference, note, media_ids::text AS \"mediaIdsJson\",",
            "ST_X(location) AS longitude, ST_Y(location) AS latitude FROM footprint",
            "<if test='year != null'>WHERE visit_date &gt;= make_date(#{year},1,1) AND visit_date &lt; make_date(#{year}+1,1,1)</if>",
            "ORDER BY visit_date DESC NULLS LAST", "</script>"})
    List<FootSpot> selectRecords(@Param("year") Integer year);

    /** 分页查询足迹记录。 */
    @SelectProvider(type = Sql.class, method = "selectPage")
    List<FootSpot> selectPage(@Param("visitType") String visitType, @Param("keyword") String keyword,
                              @Param("sortBy") String sortBy, @Param("sortOrder") String sortOrder,
                              @Param("limit") int limit, @Param("offset") long offset);

    /** 不分页查询足迹记录，用于行政区筛选等场景。 */
    @SelectProvider(type = Sql.class, method = "searchRecords")
    List<FootSpot> searchRecords(@Param("visitType") String visitType, @Param("keyword") String keyword,
                                 @Param("sortBy") String sortBy, @Param("sortOrder") String sortOrder);

    /** 统计符合条件的足迹记录数。 */
    @SelectProvider(type = Sql.class, method = "countRecords")
    long countRecords(@Param("visitType") String visitType, @Param("keyword") String keyword);

    final class Sql {
        /** 根据筛选参数构造基础足迹查询语句。 */
        private static StringBuilder base(Map<String, Object> params) {
            StringBuilder sql = new StringBuilder("SELECT footprint_id, place_name, visit_type, visit_date, poi_reference, note, media_ids::text AS \"mediaIdsJson\", ST_X(location) AS longitude, ST_Y(location) AS latitude FROM footprint WHERE 1=1");
            if (params.get("visitType") != null) sql.append(" AND visit_type=#{visitType}");
            if (params.get("keyword") != null) sql.append(" AND place_name ILIKE #{keyword}");
            return sql;
        }

        /** 生成分页足迹查询 SQL。 */
        public static String selectPage(Map<String, Object> params) {
            String sort = sortColumn(params.get("sortBy"));
            String order = "asc".equals(params.get("sortOrder")) ? "ASC" : "DESC";
            return base(params).append(" ORDER BY ").append(sort).append(' ').append(order)
                    .append(" NULLS LAST, footprint_id DESC LIMIT #{limit} OFFSET #{offset}").toString();
        }

        /** 生成不分页足迹查询 SQL。 */
        public static String searchRecords(Map<String, Object> params) {
            String sort = sortColumn(params.get("sortBy"));
            String order = "asc".equals(params.get("sortOrder")) ? "ASC" : "DESC";
            return base(params).append(" ORDER BY ").append(sort).append(' ').append(order)
                    .append(" NULLS LAST, footprint_id DESC").toString();
        }

        /** 生成足迹数量统计 SQL。 */
        public static String countRecords(Map<String, Object> params) {
            StringBuilder sql = new StringBuilder("SELECT count(*) FROM footprint WHERE 1=1");
            if (params.get("visitType") != null) sql.append(" AND visit_type=#{visitType}");
            if (params.get("keyword") != null) sql.append(" AND place_name ILIKE #{keyword}");
            return sql.toString();
        }

        /** 将排序字段映射为允许使用的数据库列名。 */
        private static String sortColumn(Object value) {
            return switch (String.valueOf(value)) {
                case "placeName" -> "place_name";
                case "visitType" -> "visit_type";
                default -> "visit_date";
            };
        }
    }
}
