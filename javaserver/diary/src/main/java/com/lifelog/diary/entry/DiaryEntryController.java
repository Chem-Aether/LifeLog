package com.lifelog.diary.entry;

import com.lifelog.common.api.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 提供日记条目、年份和统计摘要的 HTTP 接口。 */
@RestController
@RequestMapping("/diary")
public class DiaryEntryController {
    private final DiaryEntryService service;

    /**
     * 创建日记服务控制器。
     * @param service 日记条目业务服务
     */
    public DiaryEntryController(DiaryEntryService service) {
        this.service = service;
    }

    /**
     * 按条件分页查询日记。
     * @param keyword 标题、正文或地点关键字
     * @param year 日记年份
     * @param page 页码，从 1 开始
     * @param size 每页记录数
     * @return 分页日记列表
     */
    @GetMapping("/entries")
    public Result list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer year,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size) {
        return Result.success(service.list(keyword, year, page, size));
    }

    /**
     * 查询单篇日记详情。
     * @param id 日记 ID
     * @return 日记详情
     */
    @GetMapping("/entries/{id}")
    public Result get(@PathVariable long id) {
        return Result.success(service.get(id));
    }

    /**
     * 新建日记及其标签、媒体关联。
     * @param request 日记内容和关联数据
     * @return 新建后的日记
     */
    @PostMapping("/entries")
    public Result create(@RequestBody DiaryEntryRequest request) {
        return Result.success("日记已保存", service.create(request));
    }

    /**
     * 更新日记内容及其标签、媒体关联。
     * @param id 日记 ID
     * @param request 更新后的日记内容
     * @return 更新后的日记
     */
    @PutMapping("/entries/{id}")
    public Result update(@PathVariable long id, @RequestBody DiaryEntryRequest request) {
        return Result.success("日记已更新", service.update(id, request));
    }

    /**
     * 删除指定日记。
     * @param id 日记 ID
     * @return 删除结果
     */
    @DeleteMapping("/entries/{id}")
    public Result delete(@PathVariable long id) {
        service.delete(id);
        return Result.success("日记已删除");
    }

    /**
     * 查询已记录日记涉及的年份。
     * @return 按年份倒序排列的年份列表
     */
    @GetMapping("/years")
    public Result years() {
        return Result.success(service.years());
    }

    /**
     * 查询日记统计摘要。
     * @param year 可选统计年份
     * @return 统计摘要
     */
    @GetMapping("/summary")
    public Result summary(@RequestParam(required = false) Integer year) {
        return Result.success(service.summary(year));
    }
}
