package com.ops.platform.service;

import com.ops.platform.repository.MetricDataRepository;
import com.ops.platform.repository.TaskExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 历史数据保留策略：监控原始数据量随资产数和采集频率快速增长，需定期清理，
 * 避免数据库无限膨胀影响查询性能（对标商业监控软件的"数据保留期"设置项）。
 */
@Slf4j
@Service
public class DataRetentionService {

    @Autowired
    private MetricDataRepository metricDataRepository;
    @Autowired
    private TaskExecutionRepository taskExecutionRepository;

    @Value("${ops.retention.metric-days:90}")
    private int metricRetentionDays;

    public void cleanup() {
        LocalDateTime metricBefore = LocalDateTime.now().minusDays(metricRetentionDays);
        try {
            metricDataRepository.deleteByCollectTimeBefore(metricBefore);
            log.info("已清理 {} 天前的监控历史数据", metricRetentionDays);
        } catch (Exception e) {
            log.warn("清理监控历史数据失败: {}", e.getMessage());
        }
    }
}
