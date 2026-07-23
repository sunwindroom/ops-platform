package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 自动化运维任务（脚本/命令下发）
 */
@Data
@Entity
@Table(name = "ops_task")
public class OpsTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** shell / powershell */
    @Column(length = 20)
    private String scriptType = "shell";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String scriptContent;

    /** 目标资产ID，逗号分隔 */
    @Column(columnDefinition = "TEXT")
    private String targetAssetIds;

    /** 定时表达式(可空=仅手动执行)，如 0 0 2 * * ? */
    private String cronExpression;

    private Boolean enabled = true;

    private String remark;

    private LocalDateTime createTime = LocalDateTime.now();
}
