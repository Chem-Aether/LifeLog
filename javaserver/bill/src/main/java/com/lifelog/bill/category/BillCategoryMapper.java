package com.lifelog.bill.category;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/** 定义账单分类的数据访问操作。 */
@Mapper
public interface BillCategoryMapper {
    /**
     * 查询所有分类及其父级名称。
     * @return 分类列表
     */
    @Select("""
            SELECT c.id, c.name, c.icon, c.parent_id AS "parentId", p.name AS "parentName", c.sort_order AS "sortOrder"
            FROM bill_category c LEFT JOIN bill_category p ON p.id=c.parent_id
            ORDER BY COALESCE(p.sort_order,c.sort_order), p.id NULLS FIRST, c.sort_order, c.id
            """)
    List<Map<String, Object>> findAll();

    /** 查询同一父级下最大的排序值。 */
    @Select("SELECT COALESCE(MAX(sort_order),0) FROM bill_category WHERE parent_id IS NOT DISTINCT FROM #{parentId}")
    int maxSortOrder(@Param("parentId") Long parentId);

    /** 新增分类并返回数据库生成的 ID。 */
    @Select("INSERT INTO bill_category(name,parent_id,sort_order,icon) VALUES (#{name},#{parentId},#{sortOrder},#{icon}) RETURNING id")
    Long insert(@Param("name") String name, @Param("parentId") Long parentId,
                @Param("sortOrder") int sortOrder, @Param("icon") String icon);

    /** 更新分类名称及图标。 */
    @Update("UPDATE bill_category SET name=#{name}, icon=#{icon} WHERE id=#{id}")
    int update(@Param("id") long id, @Param("name") String name, @Param("icon") String icon);

    /** 删除指定分类。 */
    @Delete("DELETE FROM bill_category WHERE id=#{id}")
    int delete(long id);

    /** 判断指定 ID 是否为一级分类。 */
    @Select("SELECT COUNT(*) FROM bill_category WHERE id=#{id} AND parent_id IS NULL")
    int countParentById(long id);

    /** 判断分类是否存在。 */
    @Select("SELECT COUNT(*) FROM bill_category WHERE id=#{id}")
    int countById(long id);
}
