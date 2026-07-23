package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务执行记录（每个资产一条）
 */
@Data
@Entity
@Table(name = "task_execution")
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long taskId;
    private String taskName;
    private Long assetId;
    private String assetName;

    /** RUNNING / SUCCESS / FAILED */
    @Column(length = 20)
    private String status = "RUNNING";

    private Integer exitCode;

    @Column(columnDefinition = "TEXT")
    private String output;

    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;
}
