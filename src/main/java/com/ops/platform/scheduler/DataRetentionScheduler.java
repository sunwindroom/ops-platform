package com.ops.platform.scheduler;

import com.ops.platform.service.DataRetentionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨3点执行一次历史数据清理 */
@Component
public class DataRetentionScheduler {

    @Autowired
    private DataRetentionService dataRetentionService;

    @Scheduled(cron = "${ops.retention.cron:0 0 3 * * ?}")
    public void run() {
        dataRetentionService.cleanup();
    }
}
