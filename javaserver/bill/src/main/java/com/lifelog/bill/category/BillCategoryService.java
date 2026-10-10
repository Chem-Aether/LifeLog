package com.lifelog.bill.category;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BillCategoryService {
    private final BillCategoryMapper categoryMapper;

    public BillCategoryService(BillCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    public List<Map<String, Object>> list() {
        return categoryMapper.findAll();
    }

    public long create(CategoryRequest request) {
        validateName(request == null ? null : request.name());
        Long parentId = request.parentId();
        if (parentId != null) requireParent(parentId);
        int nextSortOrder = request.sortOrder() == null
                ? categoryMapper.maxSortOrder(parentId) + 1
                : request.sortOrder();
        try {
            return categoryMapper.insert(request.name().trim(), parentId, nextSortOrder, icon(request.icon()));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(BAD_REQUEST, "同级分类名称已存在");
        }
    }

    public void update(long id, CategoryUpdateRequest request) {
        validateName(request == null ? null : request.name());
        try {
            if (categoryMapper.update(id, request.name().trim(), icon(request.icon())) == 0)
                throw new ResponseStatusException(NOT_FOUND, "分类不存在");
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(BAD_REQUEST, "同级分类名称已存在");
        }
    }

    public void delete(long id) {
        try {
            if (categoryMapper.delete(id) == 0) throw new ResponseStatusException(NOT_FOUND, "分类不存在");
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(CONFLICT, "该分类仍被账单或子分类使用，不能删除");
        }
    }

    private void requireParent(long id) {
        if (categoryMapper.countParentById(id) == 0)
            throw new ResponseStatusException(BAD_REQUEST, "二级分类必须归属一个一级分类");
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "分类名称不能为空");
        if (name.trim().length() > 100) throw new ResponseStatusException(BAD_REQUEST, "分类名称不能超过100个字符");
    }

    private static String icon(String icon) {
        if (icon == null || icon.isBlank()) return "🏷️";
        if (icon.length() > 32000) throw new ResponseStatusException(BAD_REQUEST, "分类图标无效");
        return icon;
    }
}
