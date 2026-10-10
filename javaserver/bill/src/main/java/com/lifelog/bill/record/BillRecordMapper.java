package com.lifelog.bill.record;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Mapper
public interface BillRecordMapper {
    @SelectProvider(type = Sql.class, method = "findRecords")
    List<Map<String, Object>> findRecords(@Param("direction") String direction, @Param("accountId") Long accountId,
                                          @Param("categoryId") Long categoryId, @Param("keyword") String keyword,
                                          @Param("startTime") Timestamp startTime, @Param("endTime") Timestamp endTime);

    @Insert("""
            INSERT INTO bill_transaction(occurred_at, amount, from_account_id, to_account_id,
                counterparty, description, category_id, remark)
            VALUES (#{occurredAt}, #{amount}, #{fromAccountId}, #{toAccountId},
                #{counterparty}, #{description}, #{categoryId}, #{remark})
            """)
    int insert(BillTransactionRequest request);

    @Select("""
            INSERT INTO bill_transaction(occurred_at, amount, from_account_id, to_account_id,
                counterparty, description, category_id, remark)
            VALUES (#{occurredAt}, #{amount}, #{fromAccountId}, #{toAccountId},
                #{counterparty}, #{description}, #{categoryId}, #{remark}) RETURNING id
            """)
    Long insertReturningId(BillTransactionRequest request);

    @Update("""
            UPDATE bill_transaction SET occurred_at=#{request.occurredAt}, amount=#{request.amount},
                from_account_id=#{request.fromAccountId}, to_account_id=#{request.toAccountId},
                counterparty=#{request.counterparty}, description=#{request.description}, category_id=#{request.categoryId},
                remark=#{request.remark}, updated_at=now() WHERE id=#{id}
            """)
    int update(@Param("id") long id, @Param("request") BillTransactionRequest request);

    @Delete("DELETE FROM bill_transaction WHERE id=#{id}")
    int delete(long id);

    final class Sql {
        public Sql() {}

        public static String findRecords(Map<String, Object> params) {
            StringBuilder sql = new StringBuilder("""
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
            if (params.get("direction") != null) sql.append(" AND CASE WHEN t.from_account_id IS NULL THEN 'income' WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END=#{direction}");
            if (params.get("accountId") != null) sql.append(" AND (t.from_account_id=#{accountId} OR t.to_account_id=#{accountId})");
            if (params.get("categoryId") != null) sql.append(" AND t.category_id=#{categoryId}");
            if (params.get("keyword") != null) sql.append(" AND (t.counterparty ILIKE #{keyword} OR t.description ILIKE #{keyword} OR t.remark ILIKE #{keyword})");
            if (params.get("startTime") != null) sql.append(" AND t.occurred_at >= #{startTime}");
            if (params.get("endTime") != null) sql.append(" AND t.occurred_at <= #{endTime}");
            return sql.append(" ORDER BY t.occurred_at DESC, t.id DESC").toString();
        }
    }
}
