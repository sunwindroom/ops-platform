package com.ops.platform.scheduler;

import com.ops.platform.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时告警评估调度：每次采集完成后评估一次阈值规则。
 * 默认比采集周期稍慢，确保数据已入库。
 */
@Component
public class AlertScheduler {

    @Autowired
    private AlertService alertService;

    @Scheduled(fixedDelayString = "${ops.alert.interval-ms:60000}", initialDelay = 20000)
    public void run() {
        alertService.evaluateAll();
    }
}
