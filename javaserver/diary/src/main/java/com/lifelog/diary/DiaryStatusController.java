package com.lifelog.diary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 提供日记服务运行状态查询接口。 */
@RestController
@RequestMapping("/diary")
public class DiaryStatusController {
    /**
     * 返回日记服务的就绪状态。
     * @return 服务名称和运行状态
     */
    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("service", "diary", "status", "ready");
    }
}
