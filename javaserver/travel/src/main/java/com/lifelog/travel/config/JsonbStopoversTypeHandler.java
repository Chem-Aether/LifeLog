package com.lifelog.travel.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/** 在航班经停机场列表与 PostgreSQL JSONB 字段之间转换数据。 */
public class JsonbStopoversTypeHandler extends BaseTypeHandler<List<Map<String, Object>>> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> TYPE = new TypeReference<>() {};

    /** 将经停机场列表写入 JSONB 参数。 */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int index, List<Map<String, Object>> value,
                                    JdbcType jdbcType) throws SQLException {
        try {
            PGobject jsonb = new PGobject();
            jsonb.setType("jsonb");
            jsonb.setValue(OBJECT_MAPPER.writeValueAsString(value));
            ps.setObject(index, jsonb);
        } catch (JsonProcessingException exception) {
            throw new SQLException("经停机场数据无法序列化", exception);
        }
    }

    /** 按列名读取 JSONB 经停机场列表。 */
    @Override
    public List<Map<String, Object>> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    /** 按列序号读取 JSONB 经停机场列表。 */
    @Override
    public List<Map<String, Object>> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    /** 从存储过程结果中读取 JSONB 经停机场列表。 */
    @Override
    public List<Map<String, Object>> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private static List<Map<String, Object>> parse(String json) throws SQLException {
        if (json == null) return null;
        try {
            return OBJECT_MAPPER.readValue(json, TYPE);
        } catch (IOException exception) {
            throw new SQLException("经停机场 JSONB 数据无效", exception);
        }
    }
}
