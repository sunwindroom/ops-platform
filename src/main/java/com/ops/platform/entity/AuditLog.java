package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作审计日志：记录关键操作（登录、资产变更、任务执行、规则变更等），便于事后追溯与合规审计。
 */
@Data
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    /** 操作类型：LOGIN / ASSET_CREATE / ASSET_UPDATE / ASSET_DELETE / TASK_RUN / RULE_SAVE 等 */
    @Column(length = 50)
    private String action;

    @Column(length = 50)
    private String targetType;
    private String targetId;

    @Column(columnDefinition = "TEXT")
    private String detail;

    private String clientIp;

    private LocalDateTime createTime = LocalDateTime.now();
}
