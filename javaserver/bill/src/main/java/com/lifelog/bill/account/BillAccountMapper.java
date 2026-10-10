package com.lifelog.bill.account;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface BillAccountMapper {
    @Select("""
            SELECT a.id, a.name, a.code, a.opening_balance AS "openingBalance",
                   a.opening_balance
                     + COALESCE((SELECT SUM(t.amount) FROM bill_transaction t WHERE t.to_account_id = a.id), 0)
                     - COALESCE((SELECT SUM(t.amount) FROM bill_transaction t WHERE t.from_account_id = a.id), 0) AS balance
            FROM bill_account a ORDER BY a.id
            """)
    List<Map<String, Object>> findAll();

    @Select("INSERT INTO bill_account(name, code, opening_balance) VALUES (#{name}, #{code}, #{balance}) RETURNING id")
    Long insert(@Param("name") String name, @Param("code") String code, @Param("balance") BigDecimal balance);

    @Update("UPDATE bill_account SET name=#{name}, code=#{code}, opening_balance=#{balance}, updated_at=now() WHERE id=#{id}")
    int update(@Param("id") long id, @Param("name") String name, @Param("code") String code, @Param("balance") BigDecimal balance);

    @Delete("DELETE FROM bill_account WHERE id=#{id}")
    int delete(long id);

    @Select("SELECT COUNT(*) FROM bill_account WHERE id=#{id}")
    int countById(long id);

    @SelectProvider(type = Sql.class, method = "accountRecords")
    List<Map<String, Object>> findRecords(@Param("accountId") long accountId, @Param("sortBy") String sortBy,
                                          @Param("sortOrder") String sortOrder, @Param("limit") int limit,
                                          @Param("offset") long offset);

    @Select("SELECT COUNT(*) FROM bill_transaction WHERE from_account_id=#{accountId} OR to_account_id=#{accountId}")
    long countRecords(long accountId);

    final class Sql {
        public Sql() {}

        public static String accountRecords(Map<String, Object> params) {
            String sortColumn = switch (String.valueOf(params.get("sortBy"))) {
                case "amount" -> "t.amount";
                case "description" -> "t.description";
                case "category" -> "c.name";
                case "direction" -> "CASE WHEN t.from_account_id IS NULL THEN 'income' WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END";
                case "payer" -> "COALESCE(fa.name, t.counterparty, '')";
                case "payee" -> "COALESCE(ta.name, t.counterparty, '')";
                default -> "t.occurred_at";
            };
            String order = "asc".equalsIgnoreCase(String.valueOf(params.get("sortOrder"))) ? "ASC" : "DESC";
            return """
                    SELECT t.id, TO_CHAR(t.occurred_at, 'YYYY-MM-DD"T"HH24:MI:SS') AS "occurredAt", t.amount,
                           t.from_account_id AS "fromAccountId", t.to_account_id AS "toAccountId",
                           fa.name AS "fromAccountName", fa.code AS "fromAccountCode", ta.name AS "toAccountName",
                           ta.code AS "toAccountCode", t.counterparty, t.description, c.id AS "categoryId",
                           c.name AS category, c.icon AS "categoryIcon", p.name AS "categoryParent", t.remark,
                           CASE WHEN t.from_account_id IS NULL THEN 'income' WHEN t.to_account_id IS NULL THEN 'expense' ELSE 'transfer' END AS direction
                    FROM bill_transaction t LEFT JOIN bill_account fa ON fa.id=t.from_account_id
                    LEFT JOIN bill_account ta ON ta.id=t.to_account_id LEFT JOIN bill_category c ON c.id=t.category_id
                    LEFT JOIN bill_category p ON p.id=c.parent_id
                    WHERE (t.from_account_id=#{accountId} OR t.to_account_id=#{accountId})
                    """ + " ORDER BY " + sortColumn + " " + order + ", t.id DESC LIMIT #{limit} OFFSET #{offset}";
        }
    }
}
