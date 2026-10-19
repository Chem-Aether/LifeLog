package com.lifelog.diary.entry;

import java.time.LocalDate;
import java.util.List;

/**
 * 日记创建或更新请求。
 * @param title 标题
 * @param content 正文
 * @param entryDate 日记日期
 * @param mood 心情
 * @param weatherCode 天气现象代码
 * @param location 地点
 * @param tags 标签列表
 * @param mediaIds 媒体 ID 列表
 */
public record DiaryEntryRequest(
        String title,
        String content,
        LocalDate entryDate,
        String mood,
        Integer weatherCode,
        String location,
        List<String> tags,
        List<String> mediaIds
) {}
