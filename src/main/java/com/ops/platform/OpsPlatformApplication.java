package com.ops.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 内网自动运维管理平台
 * 功能：资产管理、监控采集、阈值告警、自动化运维
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class OpsPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpsPlatformApplication.class, args);
    }
}
