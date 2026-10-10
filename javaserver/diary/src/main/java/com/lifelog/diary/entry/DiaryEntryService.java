package com.lifelog.diary.entry;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class DiaryEntryService {
    private static final Pattern MEDIA_ID = Pattern.compile("[0-9a-f]{32}");
    private final DiaryEntryMapper mapper;

    public DiaryEntryService(DiaryEntryMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> list(String keyword, Integer year, int page, int size) {
        if (page < 1 || page > 100_000 || size < 1 || size > 100) throw badRequest("分页参数无效");
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<Map<String, Object>> items = mapper.findEntries(normalizedKeyword, year, size, (page - 1) * size);
        attachRelations(items);
        return Map.of("items", items, "total", mapper.countEntries(normalizedKeyword, year), "page", page, "size", size);
    }

    public Map<String, Object> get(long id) {
        Map<String, Object> entry = mapper.findEntry(id);
        if (entry == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
        attachRelations(List.of(entry));
        return entry;
    }

    public List<Integer> years() {
        return mapper.findYears();
    }

    public Map<String, Object> summary(Integer year) {
        return mapper.summary(year);
    }

    @Transactional
    public Map<String, Object> create(DiaryEntryRequest request) {
        validate(request);
        long id = mapper.insert(request);
        mapper.replaceRelations(id, normalizeTags(request.tags()), normalizeMediaIds(request.mediaIds()));
        return get(id);
    }

    @Transactional
    public Map<String, Object> update(long id, DiaryEntryRequest request) {
        validate(request);
        if (mapper.update(id, request) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
        mapper.replaceRelations(id, normalizeTags(request.tags()), normalizeMediaIds(request.mediaIds()));
        return get(id);
    }

    public void delete(long id) {
        if (mapper.delete(id) == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日记不存在");
    }

    private void attachRelations(List<Map<String, Object>> entries) {
        if (entries.isEmpty()) return;
        List<Long> ids = entries.stream().map(entry -> ((Number) entry.get("id")).longValue()).toList();
        Map<Long, List<String>> tags = mapper.findRelations(ids, false);
        Map<Long, List<String>> media = mapper.findRelations(ids, true);
        for (Map<String, Object> entry : entries) {
            long id = ((Number) entry.get("id")).longValue();
            entry.put("tags", tags.getOrDefault(id, List.of()));
            entry.put("mediaIds", media.getOrDefault(id, List.of()));
        }
    }

    private static void validate(DiaryEntryRequest request) {
        if (request == null || request.title() == null || request.title().isBlank()) throw badRequest("标题不能为空");
        if (request.title().trim().length() > 200) throw badRequest("标题最多200个字符");
        if (request.content() != null && request.content().length() > 20000) throw badRequest("正文最多20000个字符");
        if (request.entryDate() == null) throw badRequest("日记日期不能为空");
        if (request.mood() != null && request.mood().length() > 32) throw badRequest("心情字段过长");
        if (request.location() != null && request.location().trim().length() > 160) throw badRequest("地点最多160个字符");
        normalizeTags(request.tags());
        normalizeMediaIds(request.mediaIds());
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null) return List.of();
        if (tags.size() > 20) throw badRequest("每篇日记最多添加20个标签");
        return tags.stream().filter(tag -> tag != null && !tag.isBlank()).map(String::trim).distinct().peek(tag -> {
            if (tag.length() > 40) throw badRequest("标签最多40个字符");
        }).toList();
    }

    private static List<String> normalizeMediaIds(List<String> mediaIds) {
        if (mediaIds == null) return List.of();
        if (mediaIds.size() > 20) throw badRequest("每篇日记最多添加20张图片");
        return mediaIds.stream().filter(id -> id != null && !id.isBlank()).distinct().peek(id -> {
            if (!MEDIA_ID.matcher(id).matches()) throw badRequest("图片标识无效");
        }).toList();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
