package com.ops.platform.scheduler;

import com.ops.platform.entity.OpsTask;
import com.ops.platform.repository.OpsTaskRepository;
import com.ops.platform.service.TaskExecutionService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 支持给自动化任务配置 cron 表达式定时执行（如每天凌晨2点巡检）。
 * 应用启动时加载所有已启用且配置了cron的任务并注册；
 * 任务的增删改由 TaskController 调用 reschedule() 动态刷新。
 */
@Component
public class TaskCronScheduler {

    @Autowired
    private OpsTaskRepository opsTaskRepository;
    @Autowired
    private TaskExecutionService taskExecutionService;
    @Autowired
    private TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        List<OpsTask> tasks = opsTaskRepository.findByEnabledTrue();
        for (OpsTask t : tasks) {
            scheduleOne(t);
        }
    }

    public void reschedule(OpsTask task) {
        cancel(task.getId());
        if (Boolean.TRUE.equals(task.getEnabled())) {
            scheduleOne(task);
        }
    }

    public void cancel(Long taskId) {
        ScheduledFuture<?> f = scheduledJobs.remove(taskId);
        if (f != null) f.cancel(false);
    }

    private void scheduleOne(OpsTask task) {
        if (task.getCronExpression() == null || task.getCronExpression().isBlank()) return;
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> taskExecutionService.executeTask(task.getId()),
                    new CronTrigger(task.getCronExpression())
            );
            scheduledJobs.put(task.getId(), future);
        } catch (Exception ignored) {
            // cron表达式非法时忽略，不影响其他任务
        }
    }
}
