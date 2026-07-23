package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

/**
 * 告警静默/维护窗口（参考 Prometheus Alertmanager Silence / PRTG 暂停监控理念）
 * 在设定的时间窗口内，指定资产（或全部资产）的告警不会触发通知，避免计划性维护/变更期间产生告警噪音。
 */
@Data
@Entity
@Table(name = "alert_mute")
public class AlertMute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 为空表示对全部资产生效 */
    private Long assetId;
    private String assetName;

    private String reason;

    @Column(nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startTime;
    @Column(nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endTime;

    private String createdBy;
    private LocalDateTime createTime = LocalDateTime.now();
}
