package com.lifelog.bill.category;

import com.lifelog.bill.category.CategoryRequest;
import com.lifelog.bill.category.CategoryUpdateRequest;
import com.lifelog.common.api.Result;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bill/categories")
public class BillCategoryController {
    private final BillCategoryService categoryService;

    public BillCategoryController(BillCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public Result categories() {
        return Result.success(categoryService.list());
    }

    @PostMapping
    public Result create(@RequestBody CategoryRequest request) {
        return Result.success(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public Result update(@PathVariable long id, @RequestBody CategoryUpdateRequest request) {
        categoryService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result delete(@PathVariable long id) {
        categoryService.delete(id);
        return Result.success();
    }
}
