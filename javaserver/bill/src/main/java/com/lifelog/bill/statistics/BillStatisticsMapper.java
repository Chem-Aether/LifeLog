package com.lifelog.bill.statistics;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 定义账单统计所需的数据查询操作。 */
@Mapper
public interface BillStatisticsMapper {
    /**
     * 汇总指定时间范围内的收入、支出和转账金额。
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @return 收支统计映射
     */
    @SelectProvider(type = Sql.class, method = "summary")
    Map<String, Object> summary(@Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

    /** 汇总各账户的收入和支出。 */
    @SelectProvider(type = Sql.class, method = "accountStatistics")
    List<Map<String, Object>> accountStatistics(@Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

    /** 查询时间范围内的每日收支。 */
    @SelectProvider(type = Sql.class, method = "dailyTimeline")
    List<Map<String, Object>> dailyTimeline(@Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

    /** 查询时间范围内的每月收支。 */
    @SelectProvider(type = Sql.class, method = "monthlyTimeline")
    List<Map<String, Object>> monthlyTimeline(@Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

    /** 按账单分类汇总金额。 */
    @SelectProvider(type = Sql.class, method = "categoryStatistics")
    List<Map<String, Object>> categoryStatistics(@Param("direction") String direction,
                                                 @Param("startTime") Timestamp startTime,
                                                 @Param("endTime") Timestamp endTime,
                                                 @Param("detailed") boolean detailed,
                                                 @Param("parentCategoryId") Long parentCategoryId);

    /** 按支出金额统计商户偏好。 */
    @SelectProvider(type = Sql.class, method = "merchantPreferences")
    List<Map<String, Object>> merchantPreferences(@Param("startTime") Timestamp startTime,
                                                  @Param("endTime") Timestamp endTime);

    /** 查询支出商品描述，用于轻量拆分商品偏好。 */
    @SelectProvider(type = Sql.class, method = "expenseDescriptions")
    List<Map<String, Object>> expenseDescriptions(@Param("startTime") Timestamp startTime,
                                                  @Param("endTime") Timestamp endTime);

    final class Sql {
        /** 创建统计查询 SQL 提供器。 */
        public Sql() {}

        /**
         * 生成收支概览查询 SQL。
         * @param params 查询参数
         * @return 查询 SQL
         */
        public static String summary(Map<String, Object> params) {
            return """
                    SELECT COALESCE(SUM(amount) FILTER (WHERE from_account_id IS NULL),0) AS income,
                           COALESCE(SUM(amount) FILTER (WHERE to_account_id IS NULL),0) AS expense,
                           COALESCE(SUM(amount) FILTER (WHERE from_account_id IS NOT NULL AND to_account_id IS NOT NULL),0) AS transfer
                    FROM bill_transaction WHERE 1=1
                    """ + dateFilter(params);
        }

        /**
         * 生成分账户收支统计 SQL。
         * @param params 查询参数
         * @return 查询 SQL
         */
        public static String accountStatistics(Map<String, Object> params) {
            StringBuilder sql = new StringBuilder("""
                    SELECT a.id, a.name, a.code,
                           COALESCE(SUM(t.amount) FILTER (WHERE t.from_account_id IS NULL AND t.to_account_id=a.id),0) AS income,
                           COALESCE(SUM(t.amount) FILTER (WHERE t.from_account_id=a.id AND t.to_account_id IS NULL),0) AS expense
                    FROM bill_account a LEFT JOIN (SELECT * FROM bill_transaction WHERE 1=1
                    """);
            sql.append(dateFilter(params)).append("""
                    ) t ON ((t.from_account_id IS NULL AND t.to_account_id=a.id)
                    OR (t.from_account_id=a.id AND t.to_account_id IS NULL))
                    GROUP BY a.id, a.name, a.code ORDER BY a.id
                    """);
            return sql.toString();
        }

        /**
         * 生成每日收支时间线 SQL。
         * @param params 查询参数
         * @return 查询 SQL
         */
        public static String dailyTimeline(Map<String, Object> params) {
            String measures = "COALESCE(SUM(amount) FILTER (WHERE from_account_id IS NULL),0) AS income, "
                    + "COALESCE(SUM(amount) FILTER (WHERE to_account_id IS NULL),0) AS expense ";
            return "SELECT TO_CHAR(DATE_TRUNC('day', occurred_at), 'YYYY-MM-DD') AS period, " + measures
                    + "FROM bill_transaction WHERE occurred_at >= #{startTime} AND occurred_at < #{endTime} "
                    + "GROUP BY DATE_TRUNC('day', occurred_at) ORDER BY DATE_TRUNC('day', occurred_at)";
        }

        /**
         * 生成每月收支时间线 SQL。
         * @param params 查询参数
         * @return 查询 SQL
         */
        public static String monthlyTimeline(Map<String, Object> params) {
            String measures = "COALESCE(SUM(amount) FILTER (WHERE from_account_id IS NULL),0) AS income, "
                    + "COALESCE(SUM(amount) FILTER (WHERE to_account_id IS NULL),0) AS expense ";
            return "SELECT TO_CHAR(DATE_TRUNC('month', occurred_at), 'YYYY-MM') AS period, " + measures
                    + "FROM bill_transaction WHERE occurred_at >= #{startTime} AND occurred_at < #{endTime} "
                    + "GROUP BY DATE_TRUNC('month', occurred_at) ORDER BY DATE_TRUNC('month', occurred_at)";
        }

        /**
         * 生成按分类汇总金额的 SQL。
         * @param params 查询参数
         * @return 查询 SQL
         */
        public static String categoryStatistics(Map<String, Object> params) {
            boolean detailed = Boolean.TRUE.equals(params.get("detailed")) || params.get("parentCategoryId") != null;
            String category = params.get("parentCategoryId") != null
                    ? "CASE WHEN c.id=#{parentCategoryId} THEN '未细分' ELSE c.name END"
                    : detailed ? "c.name" : "COALESCE(p.name,c.name)";
            StringBuilder sql = new StringBuilder("SELECT ").append(category)
                    .append(" AS category, SUM(t.amount) AS total_amount FROM bill_transaction t LEFT JOIN bill_category c ON c.id=t.category_id LEFT JOIN bill_category p ON p.id=c.parent_id WHERE t.category_id IS NOT NULL");
            if (params.get("direction") != null) sql.append(" AND CASE WHEN t.from_account_id IS NULL THEN 'income' WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END=#{direction}");
            sql.append(dateFilter(params));
            if (params.get("parentCategoryId") != null) sql.append(" AND (c.id=#{parentCategoryId} OR c.parent_id=#{parentCategoryId})");
            return sql.append(detailed ? " GROUP BY c.id, c.name ORDER BY total_amount DESC" : " GROUP BY 1 ORDER BY total_amount DESC").toString();
        }

        /** 生成仅包含支出的商户统计 SQL。 */
        public static String merchantPreferences(Map<String, Object> params) {
            return """
                    SELECT BTRIM(counterparty) AS merchant, COUNT(*) AS transaction_count,
                           SUM(amount) AS total_amount
                    FROM bill_transaction
                    WHERE to_account_id IS NULL AND NULLIF(BTRIM(counterparty), '') IS NOT NULL
                    """ + dateFilter(params)
                    + " GROUP BY BTRIM(counterparty) ORDER BY total_amount DESC LIMIT 12";
        }

        /** 生成仅包含支出的商品描述查询 SQL。 */
        public static String expenseDescriptions(Map<String, Object> params) {
            return """
                    SELECT description, amount FROM bill_transaction
                    WHERE to_account_id IS NULL AND NULLIF(BTRIM(description), '') IS NOT NULL
                    """ + dateFilter(params);
        }

        /**
         * 根据起止时间生成 SQL 日期过滤片段。
         * @param params 查询参数
         * @return 日期过滤 SQL
         */
        private static String dateFilter(Map<String, Object> params) {
            StringBuilder sql = new StringBuilder();
            if (params.get("startTime") != null) sql.append(" AND occurred_at >= #{startTime}");
            if (params.get("endTime") != null) sql.append(" AND occurred_at <= #{endTime}");
            return sql.toString();
        }
    }
}
