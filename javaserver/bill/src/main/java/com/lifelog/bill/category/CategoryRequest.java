package com.lifelog.bill.category;

/**
 * 创建账单分类的请求。
 * @param name 分类名称
 * @param parentId 父分类 ID
 * @param sortOrder 排序值
 * @param icon 图标内容
 */
public record CategoryRequest(String name, Long parentId, Integer sortOrder, String icon) {}
