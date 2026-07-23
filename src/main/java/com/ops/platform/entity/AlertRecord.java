package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 告警记录
 */
@Data
@Entity
@Table(name = "alert_record")
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ruleId;
    private Long assetId;
    private String assetName;
    private String metricType;
    private Double triggerValue;
    private Double threshold;

    @Column(length = 20)
    private String level;

    /** OPEN(告警中) / RESOLVED(已恢复) / ACKED(已确认) */
    @Column(length = 20)
    private String status = "OPEN";

    /** 是否已被人工确认知晓（不影响status的开启/恢复判定逻辑） */
    private Boolean acked = false;

    /** 是否已触发过自动升级通知，避免重复升级刷屏 */
    private Boolean escalated = false;

    private String message;

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime resolveTime;
}
