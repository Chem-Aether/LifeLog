package com.lifelog.diary.entry;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 实现日记条目的查询、校验、持久化和关联数据管理。 */
@Service
public class DiaryEntryService {
    private static final Set<Integer> WEATHER_CODES = Set.of(
            1000, 1001, 1002, 1003, 1004, 1005,
            2000, 2001, 2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011,
            2020, 2021, 2022, 2023, 2024, 2025, 2026,
            3000, 3001, 3002, 3003, 3004, 3005, 3006, 3007, 3020, 3021, 3022, 3023,
            4000, 4001, 4002, 4003, 4004, 4005, 4006, 4007, 4008, 4009, 4010, 4011, 4012, 4013, 4020, 4021,
            5000, 5001, 5002, 5003, 5004, 5005, 5020, 5021, 5022, 5023, 5024,
            6000, 6001, 6002, 6003, 6004, 9000, 9001, 9010, 9011, 9020, 9021, 9022, 9999
    );
    private final DiaryEntryMapper mapper;

    /**
     * 创建日记业务服务。
     * @param mapper 日记数据访问组件
     */
    public DiaryEntryService(DiaryEntryMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按条件分页查询日记并附加标签和媒体关联。
     * @param keyword 可选查询关键字
     * @param year 可选年份
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @return 分页结果
     */
    public Map<String, Object> list(String keyword, Integer year, int page, int size) {
        if (page < 1 || page > 100_000 || size < 1 || size > 100) throw badRequest("分页参数无效");
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<Map<String, Object>> items = mapper.findEntries(normalizedKeyword, year, size, (page - 1) * size);
        attachRelations(items);
        return Map.of("items", items, "total", mapper.countEntries(normalizedKeyword, year), "page", page, "size", size);
    }

    /**
     * 查询单篇日记并附加其关联数据。
     * @param id 日记 ID
     * @return 日记详情
     * @throws ResponseStatusException 日记不存在或请求无效时抛出
     */
    public Map<String, Object> get(long id) {
        Map<String, Object> entry = mapper.findEntry(id);
        if (entry == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
        attachRelations(List.of(entry));
        return entry;
    }

    /**
     * 查询已有日记年份。
     * @return 日记年份列表
     */
    public List<Integer> years() {
        return mapper.findYears();
    }

    /**
     * 查询日记统计摘要。
     * @param year 可选统计年份
     * @return 统计摘要
     */
    public Map<String, Object> summary(Integer year) {
        return mapper.summary(year);
    }

    /**
     * 校验并创建日记及关联数据。
     * @param request 日记请求
     * @return 新建日记详情
     * @throws ResponseStatusException 请求数据无效时抛出
     */
    @Transactional
    public Map<String, Object> create(DiaryEntryRequest request) {
        validate(request);
        long id = mapper.insert(request);
        mapper.replaceRelations(id, normalizeTags(request.tags()), normalizeMediaUrls(request.mediaUrls()));
        return get(id);
    }

    /**
     * 校验并更新日记及其全部关联数据。
     * @param id 日记 ID
     * @param request 更新请求
     * @return 更新后的日记详情
     * @throws ResponseStatusException 日记不存在或请求数据无效时抛出
     */
    @Transactional
    public Map<String, Object> update(long id, DiaryEntryRequest request) {
        validate(request);
        if (mapper.update(id, request) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
        mapper.replaceRelations(id, normalizeTags(request.tags()), normalizeMediaUrls(request.mediaUrls()));
        return get(id);
    }

    /**
     * 删除指定日记。
     * @param id 日记 ID
     * @throws ResponseStatusException 日记不存在时抛出
     */
    public void delete(long id) {
        if (mapper.delete(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
    }

    /**
     * 将标签和媒体关联批量填充到日记条目中。
     * @param entries 待补充关联数据的日记列表
     */
    private void attachRelations(List<Map<String, Object>> entries) {
        if (entries.isEmpty()) return;
        List<Long> ids = entries.stream().map(entry -> ((Number) entry.get("id")).longValue()).toList();
        Map<Long, List<String>> tags = mapper.findRelations(ids, false);
        Map<Long, List<String>> media = mapper.findRelations(ids, true);
        for (Map<String, Object> entry : entries) {
            long id = ((Number) entry.get("id")).longValue();
            entry.put("tags", tags.getOrDefault(id, List.of()));
            entry.put("mediaUrls", media.getOrDefault(id, List.of()));
        }
    }

    /**
     * 校验日记主体及其标签、媒体标识。
     * @param request 待校验的日记请求
     * @throws ResponseStatusException 请求数据无效时抛出
     */
    private static void validate(DiaryEntryRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) throw badRequest("标题不能为空");
        if (request.title().trim().length() > 200) throw badRequest("标题最多200个字符");
        if (request.content() != null && request.content().length() > 20000) throw badRequest("正文最多20000个字符");
        if (request.entryDate() == null) throw badRequest("日记日期不能为空");
        if (request.mood() != null && request.mood().length() > 32) throw badRequest("心情字段过长");
        if (request.weatherCode() != null && !WEATHER_CODES.contains(request.weatherCode())) throw badRequest("天气代码无效");
        if (request.location() != null && request.location().trim().length() > 160) throw badRequest("地点最多160个字符");
        normalizeTags(request.tags());
        normalizeMediaUrls(request.mediaUrls());
    }

    /**
     * 清理标签、去除重复项并校验标签长度。
     * @param tags 原始标签列表
     * @return 规范化后的标签列表
     * @throws ResponseStatusException 标签数量或长度超限时抛出
     */
    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        if (tags.size() > 20) throw badRequest("每篇日记最多添加20个标签");
        return tags.stream().filter(tag -> tag != null && !tag.isBlank()).map(String::trim).distinct().peek(tag -> {
            if (tag.length() > 40) throw badRequest("标签最多40个字符");
        }).toList();
    }

    /**
     * 清理媒体 URL 并校验其格式。
     * @param mediaUrls 原始媒体 URL 列表
     * @return 规范化后的媒体 URL 列表
     */
    private static List<String> normalizeMediaUrls(List<String> mediaUrls) {
        if (mediaUrls == null) return List.of();
        if (mediaUrls.size() > 100) throw badRequest("媒体数量过多");
        return mediaUrls.stream().filter(url -> url != null && !url.isBlank()).map(String::trim).distinct().peek(url -> {
            if (url.length() > 2048 || !(url.startsWith("http://") || url.startsWith("https://"))) throw badRequest("媒体 URL 无效");
        }).toList();
    }

    /**
     * 创建 HTTP 400 请求异常。
     * @param message 面向调用方的错误说明
     * @return 请求异常
     */
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
