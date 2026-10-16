package com.lifelog.bill.category;

/**
 * 更新账单分类的请求。
 * @param name 分类名称
 * @param icon 图标内容
 */
public record CategoryUpdateRequest(String name, String icon) {}
