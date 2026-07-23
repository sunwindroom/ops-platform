package com.ops.platform.scheduler;

import com.ops.platform.service.CollectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时采集调度：默认每60秒采集一次全部启用监控的资产。
 * 采集周期可在 application.yml 的 ops.collect.interval-ms 中调整。
 */
@Component
public class CollectScheduler {

    @Autowired
    private CollectService collectService;

    @Scheduled(fixedDelayString = "${ops.collect.interval-ms:60000}", initialDelay = 10000)
    public void run() {
        collectService.collectAll();
    }
}
