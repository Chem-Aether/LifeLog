package com.lifelog.travel.trajectory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 提供轨迹摘要和地图几何的数据访问。 */
@Mapper
public interface TrajectoryMapper extends BaseMapper<Trajectory> {
    @Select("SELECT trajectory_id AS \"trajectoryId\", name, activity_type AS \"activityType\", started_at AS \"startedAt\", ended_at AS \"endedAt\", distance_m AS \"distanceM\", duration_s AS \"durationS\", elevation_gain_m AS \"elevationGainM\", elevation_loss_m AS \"elevationLossM\", point_count AS \"pointCount\", note, ST_AsGeoJSON(route_geometry) AS \"routeGeoJson\" FROM trajectory WHERE trajectory_id = #{id}")
    Trajectory selectWithRoute(@Param("id") Long id);

    @Select("<script>SELECT trajectory_id AS \"trajectoryId\", name, activity_type AS \"activityType\", started_at AS \"startedAt\", ended_at AS \"endedAt\", distance_m AS \"distanceM\", duration_s AS \"durationS\", elevation_gain_m AS \"elevationGainM\", elevation_loss_m AS \"elevationLossM\", point_count AS \"pointCount\", note FROM trajectory <where><if test='date != null'>AND started_at &gt;= (#{date}::date AT TIME ZONE 'Asia/Shanghai') AND started_at &lt; ((#{date}::date + INTERVAL '1 day') AT TIME ZONE 'Asia/Shanghai')</if><if test='keyword != null and keyword != \"\"'>AND name ILIKE CONCAT('%', #{keyword}, '%')</if><if test='activityType != null and activityType != \"\"'>AND activity_type = #{activityType}</if></where> ORDER BY ${sortBy} ${sortOrder}, trajectory_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Trajectory> selectPage(@Param("keyword") String keyword, @Param("activityType") String activityType,
                                 @Param("date") java.time.LocalDate date,
                                 @Param("sortBy") String sortBy, @Param("sortOrder") String sortOrder,
                                 @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT count(*) FROM trajectory <where><if test='date != null'>AND started_at &gt;= (#{date}::date AT TIME ZONE 'Asia/Shanghai') AND started_at &lt; ((#{date}::date + INTERVAL '1 day') AT TIME ZONE 'Asia/Shanghai')</if><if test='keyword != null and keyword != \"\"'>AND name ILIKE CONCAT('%', #{keyword}, '%')</if><if test='activityType != null and activityType != \"\"'>AND activity_type = #{activityType}</if></where></script>")
    long countPage(@Param("keyword") String keyword, @Param("activityType") String activityType,
                   @Param("date") java.time.LocalDate date);

    @SelectProvider(type = Sql.class, method = "mapRoutes")
    List<Trajectory> selectMapRoutes(@Param("west") double west, @Param("south") double south,
                                     @Param("east") double east, @Param("north") double north,
                                     @Param("zoom") double zoom, @Param("year") Integer year);

    @Update("UPDATE trajectory SET route_geometry = (SELECT ST_Multi(ST_Collect(segment_geometry)) FROM (SELECT ST_MakeLine(location ORDER BY sequence) AS segment_geometry FROM trajectory_point WHERE trajectory_id = #{id} GROUP BY segment_no ORDER BY segment_no) segments), point_count = (SELECT count(*) FROM trajectory_point WHERE trajectory_id = #{id}) WHERE trajectory_id = #{id}")
    int rebuildRoute(@Param("id") Long id);

    final class Sql {
        public static String mapRoutes(java.util.Map<String, Object> params) {
            String tolerance = "CASE WHEN #{zoom} < 10 THEN 0.0005 WHEN #{zoom} < 12 THEN 0.0003 WHEN #{zoom} < 14 THEN 0.00008 ELSE 0.000015 END";
            String envelope = "ST_MakeEnvelope(#{west}, #{south}, #{east}, #{north}, 4326)";
            StringBuilder sql = new StringBuilder("SELECT trajectory_id AS \"trajectoryId\", name, activity_type AS \"activityType\", started_at AS \"startedAt\", ended_at AS \"endedAt\", distance_m AS \"distanceM\", duration_s AS \"durationS\", ST_AsGeoJSON(ST_Simplify(ST_CollectionExtract(ST_Intersection(route_geometry, ")
                    .append(envelope).append("), 2), ").append(tolerance).append(")) AS \"routeGeoJson\" FROM trajectory WHERE route_geometry && ").append(envelope).append(" AND ST_Intersects(route_geometry, ").append(envelope).append(")");
            if (params.get("year") != null) sql.append(" AND started_at >= make_timestamptz(#{year},1,1,0,0,0) AND started_at < make_timestamptz(#{year}+1,1,1,0,0,0)");
            return sql.append(" ORDER BY started_at DESC LIMIT 1500").toString();
        }
    }
}
