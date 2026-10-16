package com.lifelog.bill.record;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/** 定义账单记录的 SQL 查询和持久化操作。 */
@Mapper
public interface BillRecordMapper {
    /**
     * 按筛选条件查询账单记录。
     * @param direction 收支方向
     * @param accountId 账户 ID
     * @param categoryId 分类 ID
     * @param keyword 模糊搜索条件
     * @param startTime 起始时间
     * @param endTime 截止时间
     * @return 账单记录列表
     */
    @SelectProvider(type = Sql.class, method = "findRecords")
    List<Map<String, Object>> findRecords(@Param("direction") String direction, @Param("accountId") Long accountId,
                                          @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                                          @Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

    /** 按筛选、排序和分页条件查询账单记录。 */
    @SelectProvider(type = Sql.class, method = "findRecordsPage")
    List<Map<String, Object>> findRecordsPage(@Param("direction") String direction, @Param("accountId") Long accountId,
                                              @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                                              @Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime,
                                              @Param("pageSize") int pageSize, @Param("offset") long offset,
                                              @Param("sortBy") String sortBy, @Param("sortOrder") String sortOrder,
                                              @Param("excludeTransfer") boolean excludeTransfer);

    /** 统计符合筛选条件的账单记录数量。 */
    @SelectProvider(type = Sql.class, method = "countRecords")
    long countRecords(@Param("direction") String direction, @Param("accountId") Long accountId,
                      @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                      @Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime,
                      @Param("excludeTransfer") boolean excludeTransfer);

    /** 根据 ID 查询账单及关联账户、分类信息。 */
    @Select("""
            SELECT t.id, TO_CHAR(t.occurred_at, 'YYYY-MM-DD"T"HH24:MI:SS') AS "occurredAt", t.amount,
                   t.from_account_id AS "fromAccountId", t.to_account_id AS "toAccountId",
                   fa.name AS "fromAccountName", fa.code AS "fromAccountCode",
                   ta.name AS "toAccountName", ta.code AS "toAccountCode", t.counterparty, t.description,
                   c.id AS "categoryId", c.name AS category, c.icon AS "categoryIcon",
                   p.name AS "categoryParent", t.remark,
                   CASE WHEN t.from_account_id IS NULL THEN 'income'
                        WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END AS direction
            FROM bill_transaction t
            LEFT JOIN bill_account fa ON fa.id=t.from_account_id
            LEFT JOIN bill_account ta ON ta.id=t.to_account_id
            LEFT JOIN bill_category c ON c.id=t.category_id
            LEFT JOIN bill_category p ON p.id=c.parent_id
            WHERE t.id=#{id}
            """)
    Map<String, Object> findRecordById(@Param("id") long id);

    /** 插入一条账单记录，不返回数据库生成的 ID。 */
    @Insert("""
            INSERT INTO bill_transaction(occurred_at, amount, from_account_id, to_account_id,
                counterparty, description, category_id, remark)
            VALUES (#{occurredAt}, #{amount}, #{fromAccountId}, #{toAccountId},
                #{counterparty}, #{description}, #{categoryId}, #{remark})
            """)
    int insert(BillTransactionRequest request);

    /**
     * 插入账单记录并返回数据库生成的 ID。
     * @param request 账单记录请求
     * @return 新记录 ID
     */
    @Select("""
            INSERT INTO bill_transaction(occurred_at, amount, from_account_id, to_account_id,
                counterparty, description, category_id, remark)
            VALUES (#{occurredAt}, #{amount}, #{fromAccountId}, #{toAccountId},
                #{counterparty}, #{description}, #{categoryId}, #{remark}) RETURNING id
            """)
    Long insertReturningId(BillTransactionRequest request);

    /** 更新账单记录。 */
    @Update("""
            UPDATE bill_transaction SET occurred_at=#{request.occurredAt}, amount=#{request.amount},
                from_account_id=#{request.fromAccountId}, to_account_id=#{request.toAccountId},
                counterparty=#{request.counterparty}, description=#{request.description}, category_id=#{request.categoryId},
                remark=#{request.remark}, updated_at=now() WHERE id=#{id}
            """)
    int update(@Param("id") long id, @Param("request") BillTransactionRequest request);

    /** 删除指定账单记录。 */
    @Delete("DELETE FROM bill_transaction WHERE id=#{id}")
    int delete(long id);

    final class Sql {
        /** 创建动态 SQL 提供器。 */
        public Sql() {}

        /**
         * 生成不分页的账单记录查询 SQL。
         * @param params MyBatis 查询参数
         * @return 查询 SQL
         */
        public static String findRecords(Map<String, Object> params) {
            StringBuilder sql = selectRecordsSql();
            appendFilters(sql, params);
            return sql.append(" ORDER BY t.occurred_at DESC, t.id DESC").toString();
        }

        /**
         * 生成包含筛选、排序和分页条件的查询 SQL。
         * @param params MyBatis 查询参数
         * @return 查询 SQL
         */
        public static String findRecordsPage(Map<String, Object> params) {
            StringBuilder sql = selectRecordsSql();
            appendFilters(sql, params);
            String sort = switch (String.valueOf(params.get("sortBy"))) {
                case "amount" -> "t.amount";
                case "description" -> "t.description";
                case "category" -> "c.name";
                case "direction" -> "CASE WHEN t.from_account_id IS NULL THEN 'income' WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END";
                case "payer" -> "COALESCE(fa.name, t.counterparty)";
                case "payee" -> "COALESCE(ta.name, t.counterparty)";
                default -> "t.occurred_at";
            };
            String order = "asc".equals(params.get("sortOrder")) ? "ASC" : "DESC";
            appendTransferFilter(sql, params);
            return sql.append(" ORDER BY ").append(sort).append(' ').append(order)
                    .append(", t.occurred_at DESC, t.id DESC LIMIT #{pageSize} OFFSET #{offset}").toString();
        }

        /**
         * 生成符合筛选条件的记录计数 SQL。
         * @param params MyBatis 查询参数
         * @return 计数 SQL
         */
        public static String countRecords(Map<String, Object> params) {
            StringBuilder sql = new StringBuilder("SELECT count(*) FROM bill_transaction t WHERE 1=1");
            appendFilters(sql, params);
            appendTransferFilter(sql, params);
            return sql.toString();
        }

        /**
         * 构造账单记录基础查询 SQL。
         * @return 查询构造器
         */
        private static StringBuilder selectRecordsSql() {
            return new StringBuilder("""
                    SELECT t.id, TO_CHAR(t.occurred_at, 'YYYY-MM-DD"T"HH24:MI:SS') AS "occurredAt", t.amount,
                           t.from_account_id AS "fromAccountId", t.to_account_id AS "toAccountId",
                           fa.name AS "fromAccountName", fa.code AS "fromAccountCode",
                           ta.name AS "toAccountName", ta.code AS "toAccountCode",
                           t.counterparty, t.description, c.id AS "categoryId", c.name AS category,
                           c.icon AS "categoryIcon", p.name AS "categoryParent", t.remark,
                           CASE WHEN t.from_account_id IS NULL THEN 'income'
                                WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END AS direction
                    FROM bill_transaction t
                    LEFT JOIN bill_account fa ON fa.id=t.from_account_id
                    LEFT JOIN bill_account ta ON ta.id=t.to_account_id
                    LEFT JOIN bill_category c ON c.id=t.category_id
                    LEFT JOIN bill_category p ON p.id=c.parent_id
                    WHERE 1=1
                    """);
        }

        /**
         * 向 SQL 追加方向、账户、分类、关键字和时间过滤条件。
         * @param sql SQL 构造器
         * @param params 查询参数
         */
        private static void appendFilters(StringBuilder sql, Map<String, Object> params) {
            if (params.get("direction") != null) sql.append(" AND CASE WHEN t.from_account_id IS NULL THEN 'income' WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END=#{direction}");
            if (params.get("accountId") != null) sql.append(" AND (t.from_account_id=#{accountId} OR t.to_account_id=#{accountId})");
            if (params.get("categoryId") != null) sql.append(" AND t.category_id=#{categoryId}");
            if (params.get("keyword") != null) sql.append(" AND (t.counterparty ILIKE #{keyword} OR t.description ILIKE #{keyword} OR t.remark ILIKE #{keyword})");
            if (params.get("startTime") != null) sql.append(" AND t.occurred_at >= #{startTime}");
            if (params.get("endTime") != null) sql.append(" AND t.occurred_at <= #{endTime}");
        }

        /**
         * 根据参数决定是否排除转账记录。
         * @param sql SQL 构造器
         * @param params 查询参数
         */
        private static void appendTransferFilter(StringBuilder sql, Map<String, Object> params) {
            if (Boolean.TRUE.equals(params.get("excludeTransfer")))
                sql.append(" AND (t.from_account_id IS NULL OR t.to_account_id IS NULL)");
        }
    }
}
