package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 告警规则
 */
@Data
@Entity
@Table(name = "alert_rule")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** 为空表示对assetCategory下所有资产生效；不为空则只对该资产生效 */
    private Long assetId;

    /** 适用的资产大类，assetId为空时按此匹配，ALL表示全部 */
    @Column(length = 20)
    private String assetType = "ALL";

    @Column(nullable = false, length = 40)
    private String metricType;

    /** > , >= , < , <= , == */
    @Column(length = 5)
    private String operator = ">=";

    @Column(nullable = false)
    private Double threshold;

    /** 连续满足N次采集周期才触发，避免抖动 */
    private Integer consecutiveTimes = 1;

    /** info / warning / critical */
    @Column(length = 20)
    private String level = "warning";

    private Boolean enabled = true;

    private Boolean notifyEmail = true;
    private Boolean notifyWebhook = false;

    /** 告警升级：OPEN状态超过N分钟未恢复/未处理，则自动升级级别并再次强提醒（0或空=不升级） */
    private Integer escalateMinutes;
    /** 升级后的级别，默认critical */
    private String escalateToLevel = "critical";

    private LocalDateTime createTime = LocalDateTime.now();
}
