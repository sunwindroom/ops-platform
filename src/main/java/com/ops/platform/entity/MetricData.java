package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 监控指标原始数据
 */
@Data
@Entity
@Table(name = "metric_data", indexes = {
        @Index(name = "idx_asset_metric_time", columnList = "assetId,metricType,collectTime")
})
public class MetricData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long assetId;

    /** cpu_usage, mem_usage, disk_usage, net_in_kbps, net_out_kbps, if_status, ping_rtt 等 */
    @Column(nullable = false, length = 40)
    private String metricType;

    /** 附加维度，例如磁盘分区名/网卡接口名，无则为空 */
    private String dimension;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private LocalDateTime collectTime = LocalDateTime.now();
}
