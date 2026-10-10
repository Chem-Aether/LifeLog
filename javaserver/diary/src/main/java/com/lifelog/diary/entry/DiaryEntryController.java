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

@RestController
@RequestMapping("/diary")
public class DiaryEntryController {
    private final DiaryEntryService service;

    public DiaryEntryController(DiaryEntryService service) {
        this.service = service;
    }

    @GetMapping("/entries")
    public Result list(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer year,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size) {
        return Result.success(service.list(keyword, year, page, size));
    }

    @GetMapping("/entries/{id}")
    public Result get(@PathVariable long id) {
        return Result.success(service.get(id));
    }

    @PostMapping("/entries")
    public Result create(@RequestBody DiaryEntryRequest request) {
        return Result.success("日记已保存", service.create(request));
    }

    @PutMapping("/entries/{id}")
    public Result update(@PathVariable long id, @RequestBody DiaryEntryRequest request) {
        return Result.success("日记已更新", service.update(id, request));
    }

    @DeleteMapping("/entries/{id}")
    public Result delete(@PathVariable long id) {
        service.delete(id);
        return Result.success("日记已删除");
    }

    @GetMapping("/years")
    public Result years() {
        return Result.success(service.years());
    }

    @GetMapping("/summary")
    public Result summary(@RequestParam(required = false) Integer year) {
        return Result.success(service.summary(year));
    }
}
