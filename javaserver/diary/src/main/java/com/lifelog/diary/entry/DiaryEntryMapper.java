package com.lifelog.diary.entry;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 执行日记条目及其标签、媒体关系的 SQL 数据访问操作。 */
@Repository
public class DiaryEntryMapper {
    private final NamedParameterJdbcTemplate jdbc;

    /**
     * 创建日记数据访问组件。
     * @param jdbc 命名参数 JDBC 模板
     */
    public DiaryEntryMapper(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询指定条件和分页范围内的日记条目。
     * @param keyword 可选查询关键字
     * @param year 可选日记年份
     * @param limit 查询条数
     * @param offset 起始偏移量
     * @return 日记条目列表
     */
    public List<Map<String, Object>> findEntries(String keyword, Integer year, int limit, int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("""
                SELECT e.diary_id AS id, e.title, e.content, e.entry_date AS "entryDate",
                       e.mood, e.location, e.created_at AS "createdAt", e.updated_at AS "updatedAt"
                FROM diary_entry e
                WHERE 1=1
                """);
        appendFilters(sql, params, keyword, year);
        sql.append(" ORDER BY e.entry_date DESC, e.diary_id DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", limit).addValue("offset", offset);
        return jdbc.queryForList(sql.toString(), params);
    }

    /**
     * 统计符合查询条件的日记数量。
     * @param keyword 可选查询关键字
     * @param year 可选日记年份
     * @return 匹配条目数
     */
    public long countEntries(String keyword, Integer year) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder sql = new StringBuilder("SELECT count(*) FROM diary_entry e WHERE 1=1");
        appendFilters(sql, params, keyword, year);
        Long count = jdbc.queryForObject(sql.toString(), params, Long.class);
        return count == null ? 0 : count;
    }

    /**
     * 根据 ID 查询单篇日记。
     * @param id 日记 ID
     * @return 日记数据；不存在时返回 {@code null}
     */
    public Map<String, Object> findEntry(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT diary_id AS id, title, content, entry_date AS "entryDate", mood, location,
                       created_at AS "createdAt", updated_at AS "updatedAt"
                FROM diary_entry WHERE diary_id=:id
                """, Map.of("id", id));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * 查询所有已使用的日记年份。
     * @return 按年份倒序排列的年份列表
     */
    public List<Integer> findYears() {
        return jdbc.queryForList("SELECT DISTINCT EXTRACT(YEAR FROM entry_date)::int FROM diary_entry ORDER BY 1 DESC", Map.of(), Integer.class);
    }

    /**
     * 汇总日记数量、记录天数及本月条目数。
     * @param year 可选统计年份
     * @return 统计指标映射
     */
    public Map<String, Object> summary(Integer year) {
        StringBuilder sql = new StringBuilder("""
                SELECT count(*) AS entries,
                       count(DISTINCT entry_date) AS "daysWritten",
                       count(*) FILTER (WHERE entry_date >= date_trunc('month', current_date)::date AND entry_date <= current_date) AS "thisMonth"
                FROM diary_entry
                """);
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (year != null) {
            sql.append(" WHERE EXTRACT(YEAR FROM entry_date)=:year");
            params.addValue("year", year);
        }
        return jdbc.queryForMap(sql.toString(), params);
    }

    /**
     * 插入日记条目并返回数据库生成的 ID。
     * @param request 日记内容
     * @return 新日记 ID
     */
    public long insert(DiaryEntryRequest request) {
        return jdbc.queryForObject("""
                INSERT INTO diary_entry(title, content, entry_date, mood, location)
                VALUES (:title, :content, :entryDate, :mood, :location) RETURNING diary_id
                """, entryParams(request), Long.class);
    }

    /**
     * 更新指定日记条目。
     * @param id 日记 ID
     * @param request 更新后的日记内容
     * @return 更新的记录数
     */
    public int update(long id, DiaryEntryRequest request) {
        MapSqlParameterSource params = entryParams(request).addValue("id", id);
        return jdbc.update("""
                UPDATE diary_entry SET title=:title, content=:content, entry_date=:entryDate,
                       mood=:mood, location=:location, updated_at=CURRENT_TIMESTAMP
                WHERE diary_id=:id
                """, params);
    }

    /**
     * 删除指定日记条目。
     * @param id 日记 ID
     * @return 删除的记录数
     */
    public int delete(long id) {
        return jdbc.update("DELETE FROM diary_entry WHERE diary_id=:id", Map.of("id", id));
    }

    /**
     * 替换日记关联的标签和媒体记录。
     * @param id 日记 ID
     * @param tags 标签列表
     * @param mediaIds 媒体 ID 列表
     */
    public void replaceRelations(long id, List<String> tags, List<String> mediaIds) {
        jdbc.update("DELETE FROM diary_entry_tag WHERE diary_id=:id", Map.of("id", id));
        jdbc.update("DELETE FROM diary_entry_media WHERE diary_id=:id", Map.of("id", id));
        for (String tag : tags) {
            jdbc.update("INSERT INTO diary_tag(name) VALUES (:name) ON CONFLICT (name) DO NOTHING", Map.of("name", tag));
            jdbc.update("""
                    INSERT INTO diary_entry_tag(diary_id, tag_id)
                    SELECT :id, tag_id FROM diary_tag WHERE name=:name
                    ON CONFLICT DO NOTHING
                    """, Map.of("id", id, "name", tag));
        }
        for (int i = 0; i < mediaIds.size(); i++) {
            jdbc.update("INSERT INTO diary_entry_media(diary_id, media_id, sort_order) VALUES (:id, :mediaId, :sortOrder)",
                    Map.of("id", id, "mediaId", mediaIds.get(i), "sortOrder", i));
        }
    }

    /**
     * 批量查询多个日记关联的标签或媒体 ID。
     * @param ids 日记 ID 列表
     * @param media 为 {@code true} 时查询媒体，否则查询标签
     * @return 以日记 ID 为键的关联值映射
     */
    public Map<Long, List<String>> findRelations(List<Long> ids, boolean media) {
        if (ids.isEmpty()) return Map.of();
        String sql = media
                ? "SELECT diary_id, media_id AS value FROM diary_entry_media WHERE diary_id IN (:ids) ORDER BY diary_id, sort_order"
                : "SELECT et.diary_id, t.name AS value FROM diary_entry_tag et JOIN diary_tag t USING(tag_id) WHERE et.diary_id IN (:ids) ORDER BY et.diary_id, t.name";
        Map<Long, List<String>> result = new HashMap<>();
        jdbc.query(sql, Map.of("ids", ids), (RowCallbackHandler) rs -> {
            result.computeIfAbsent(rs.getLong("diary_id"), key -> new ArrayList<>()).add(rs.getString("value"));
        });
        return result;
    }

    /**
     * 根据可选关键字和年份向 SQL 追加过滤条件。
     * @param sql 待追加条件的 SQL 构造器
     * @param params SQL 参数容器
     * @param keyword 可选查询关键字
     * @param year 可选日记年份
     */
    private static void appendFilters(StringBuilder sql, MapSqlParameterSource params, String keyword, Integer year) {
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.title ILIKE :keyword OR e.content ILIKE :keyword OR e.location ILIKE :keyword)");
            params.addValue("keyword", "%" + keyword + "%");
        }
        if (year != null) {
            sql.append(" AND EXTRACT(YEAR FROM e.entry_date)=:year");
            params.addValue("year", year);
        }
    }

    /**
     * 将日记请求转换为 SQL 命名参数。
     * @param request 日记请求
     * @return SQL 参数容器
     */
    private static MapSqlParameterSource entryParams(DiaryEntryRequest request) {
        return new MapSqlParameterSource()
                .addValue("title", request.title().trim())
                .addValue("content", request.content() == null ? "" : request.content().trim())
                .addValue("entryDate", request.entryDate())
                .addValue("mood", request.mood() == null ? "" : request.mood())
                .addValue("location", request.location() == null ? "" : request.location().trim());
    }
}
