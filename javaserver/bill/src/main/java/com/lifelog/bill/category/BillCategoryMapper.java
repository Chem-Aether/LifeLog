package com.lifelog.bill.category;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface BillCategoryMapper {
    @Select("""
            SELECT c.id, c.name, c.icon, c.parent_id AS "parentId", p.name AS "parentName", c.sort_order AS "sortOrder"
            FROM bill_category c LEFT JOIN bill_category p ON p.id=c.parent_id
            ORDER BY COALESCE(p.sort_order,c.sort_order), p.id NULLS FIRST, c.sort_order, c.id
            """)
    List<Map<String, Object>> findAll();

    @Select("SELECT COALESCE(MAX(sort_order),0) FROM bill_category WHERE parent_id IS NOT DISTINCT FROM #{parentId}")
    int maxSortOrder(@Param("parentId") Long parentId);

    @Select("INSERT INTO bill_category(name,parent_id,sort_order,icon) VALUES (#{name},#{parentId},#{sortOrder},#{icon}) RETURNING id")
    Long insert(@Param("name") String name, @Param("parentId") Long parentId,
                @Param("sortOrder") int sortOrder, @Param("icon") String icon);

    @Update("UPDATE bill_category SET name=#{name}, icon=#{icon} WHERE id=#{id}")
    int update(@Param("id") long id, @Param("name") String name, @Param("icon") String icon);

    @Delete("DELETE FROM bill_category WHERE id=#{id}")
    int delete(long id);

    @Select("SELECT COUNT(*) FROM bill_category WHERE id=#{id} AND parent_id IS NULL")
    int countParentById(long id);

    @Select("SELECT COUNT(*) FROM bill_category WHERE id=#{id}")
    int countById(long id);
}
