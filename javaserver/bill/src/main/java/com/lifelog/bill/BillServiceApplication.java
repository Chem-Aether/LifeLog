package com.lifelog.bill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.lifelog")
@MapperScan({"com.lifelog.bill.account", "com.lifelog.bill.category", "com.lifelog.bill.record", "com.lifelog.bill.statistics"})
public class BillServiceApplication {
    public static void main(String[] args) { SpringApplication.run(BillServiceApplication.class, args); }
}
