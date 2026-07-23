package com.ops.platform.service;

import com.ops.platform.entity.Asset;
import com.ops.platform.entity.OpsTask;
import com.ops.platform.entity.TaskExecution;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.repository.OpsTaskRepository;
import com.ops.platform.repository.TaskExecutionRepository;
import com.ops.platform.service.collector.SshCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class TaskExecutionService {

    @Autowired private OpsTaskRepository opsTaskRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TaskExecutionRepository taskExecutionRepository;
    @Autowired private SshCollector sshCollector;

    /** 手动/定时触发任务：对任务配置的所有目标资产异步下发脚本 */
    @Async
    public void executeTask(Long taskId) {
        OpsTask task = opsTaskRepository.findById(taskId).orElse(null);
        if (task == null || !Boolean.TRUE.equals(task.getEnabled())) return;
        if (task.getTargetAssetIds() == null || task.getTargetAssetIds().isBlank()) return;

        List<Long> assetIds = Arrays.stream(task.getTargetAssetIds().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(Long::parseLong).toList();

        for (Long assetId : assetIds) {
            Asset asset = assetRepository.findById(assetId).orElse(null);
            if (asset == null) continue;
            runOnAsset(task, asset);
        }
    }

    /** 立即在单个资产上执行（用于"重启服务/一键处置"等场景，同步调用） */
    public TaskExecution runOnAsset(OpsTask task, Asset asset) {
        TaskExecution execution = new TaskExecution();
        execution.setTaskId(task.getId());
        execution.setTaskName(task.getName());
        execution.setAssetId(asset.getId());
        execution.setAssetName(asset.getName());
        execution.setStatus("RUNNING");
        execution.setStartTime(LocalDateTime.now());
        execution = taskExecutionRepository.save(execution);

        try {
            SshCollector.ExecResult result = sshCollector.runScript(asset, task.getScriptType(), task.getScriptContent());
            execution.setExitCode(result.exitCode);
            execution.setOutput(result.output);
            execution.setStatus(result.exitCode == 0 ? "SUCCESS" : "FAILED");
        } catch (Exception e) {
            execution.setStatus("FAILED");
            execution.setOutput("执行异常: " + e.getMessage());
        }
        execution.setEndTime(LocalDateTime.now());
        return taskExecutionRepository.save(execution);
    }
}
