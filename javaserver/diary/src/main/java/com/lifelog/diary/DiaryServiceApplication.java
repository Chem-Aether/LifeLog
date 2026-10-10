package com.lifelog.diary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.lifelog.diary", "com.lifelog.common"})
public class DiaryServiceApplication {
    public static void main(String[] args) { SpringApplication.run(DiaryServiceApplication.class, args); }
}
