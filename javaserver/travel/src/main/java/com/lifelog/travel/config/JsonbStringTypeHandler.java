package com.lifelog.travel.config;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.*;

/** 在 Java 字符串与 PostgreSQL JSONB 字段之间转换数据。 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {
    /**
     * 将非空字符串作为 JSONB 参数写入预编译语句。
     * @param ps JDBC 预编译语句
     * @param i 参数序号
     * @param parameter JSON 字符串
     * @param jdbcType JDBC 字段类型
     * @throws SQLException JDBC 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue(parameter);
        ps.setObject(i, value);
    }

    /**
     * 按列名读取 JSONB 字符串。
     * @param rs 查询结果集
     * @param columnName 列名
     * @return JSON 字符串
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    /**
     * 按列序号读取 JSONB 字符串。
     * @param rs 查询结果集
     * @param columnIndex 列序号
     * @return JSON 字符串
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    /**
     * 从存储过程结果中按列序号读取 JSONB 字符串。
     * @param cs 存储过程调用语句
     * @param columnIndex 列序号
     * @return JSON 字符串
     * @throws SQLException JDBC 读取失败时抛出
     */
    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
