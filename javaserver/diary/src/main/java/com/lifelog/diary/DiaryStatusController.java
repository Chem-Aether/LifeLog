package com.lifelog.diary;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/diary")
public class DiaryStatusController {
    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("service", "diary", "status", "ready");
    }
}
