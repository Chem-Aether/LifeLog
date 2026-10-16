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

/** 处理账单分类的校验、增删改查及层级约束。 */
@Service
public class BillCategoryService {
    private final BillCategoryMapper categoryMapper;

    /**
     * 创建分类业务服务。
     * @param categoryMapper 分类数据访问组件
     */
    public BillCategoryService(BillCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    /**
     * 查询全部分类。
     * @return 分类列表
     */
    public List<Map<String, Object>> list() {
        return categoryMapper.findAll();
    }

    /**
     * 校验并创建分类。
     * @param request 分类创建请求
     * @return 新分类 ID
     */
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

    /**
     * 更新分类名称及图标。
     * @param id 分类 ID
     * @param request 分类更新请求
     * @throws ResponseStatusException 分类不存在、重名或请求无效时抛出
     */
    public void update(long id, CategoryUpdateRequest request) {
        validateName(request == null ? null : request.name());
        try {
            if (categoryMapper.update(id, request.name().trim(), icon(request.icon())) == 0)
                throw new ResponseStatusException(NOT_FOUND, "分类不存在");
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(BAD_REQUEST, "同级分类名称已存在");
        }
    }

    /**
     * 删除分类。
     * @param id 分类 ID
     * @throws ResponseStatusException 分类不存在或仍被引用时抛出
     */
    public void delete(long id) {
        try {
            if (categoryMapper.delete(id) == 0) throw new ResponseStatusException(NOT_FOUND, "分类不存在");
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(CONFLICT, "该分类仍被账单或子分类使用，不能删除");
        }
    }

    /**
     * 确认指定父级存在且为一级分类。
     * @param id 父分类 ID
     * @throws ResponseStatusException 父分类不存在或不是一级分类时抛出
     */
    private void requireParent(long id) {
        if (categoryMapper.countParentById(id) == 0)
            throw new ResponseStatusException(BAD_REQUEST, "二级分类必须归属一个一级分类");
    }

    /**
     * 校验分类名称是否为空或超长。
     * @param name 分类名称
     * @throws ResponseStatusException 名称无效时抛出
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) throw new ResponseStatusException(BAD_REQUEST, "分类名称不能为空");
        if (name.trim().length() > 100) throw new ResponseStatusException(BAD_REQUEST, "分类名称不能超过100个字符");
    }

    /**
     * 规范化并校验分类图标。
     * @param icon 原始图标内容
     * @return 可保存的图标内容
     */
    private static String icon(String icon) {
        if (icon == null || icon.isBlank()) return "🏷️";
        if (icon.length() > 32000) throw new ResponseStatusException(BAD_REQUEST, "分类图标无效");
        return icon;
    }
}
