package com.lifelog.bill.category;

import com.lifelog.common.api.Result;
import org.springframework.web.bind.annotation.*;

/** 提供账单分类的查询、创建、更新和删除接口。 */
@RestController
@RequestMapping("/bill/categories")
public class BillCategoryController {
    private final BillCategoryService categoryService;

    /**
     * 创建分类控制器。
     * @param categoryService 分类业务服务
     */
    public BillCategoryController(BillCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * 查询账单分类列表。
     * @return 分类列表
     */
    @GetMapping
    public Result categories() {
        return Result.success(categoryService.list());
    }

    /**
     * 创建账单分类。
     * @param request 分类创建请求
     * @return 新分类 ID
     */
    @PostMapping
    public Result create(@RequestBody CategoryRequest request) {
        return Result.success(categoryService.create(request));
    }

    /**
     * 更新账单分类。
     * @param id 分类 ID
     * @param request 分类更新请求
     * @return 更新结果
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable long id, @RequestBody CategoryUpdateRequest request) {
        categoryService.update(id, request);
        return Result.success();
    }

    /**
     * 删除账单分类。
     * @param id 分类 ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
