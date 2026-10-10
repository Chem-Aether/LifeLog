package com.lifelog.bill.category;

public record CategoryRequest(String name, Long parentId, Integer sortOrder, String icon) {}
