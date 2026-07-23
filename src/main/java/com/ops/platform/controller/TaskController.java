package com.ops.platform.controller;

import com.ops.platform.entity.OpsTask;
import com.ops.platform.repository.OpsTaskRepository;
import com.ops.platform.repository.TaskExecutionRepository;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.scheduler.TaskCronScheduler;
import com.ops.platform.service.AuditLogService;
import com.ops.platform.service.TaskExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/tasks")
public class TaskController {

    @Autowired private OpsTaskRepository opsTaskRepository;
    @Autowired private TaskExecutionRepository taskExecutionRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TaskExecutionService taskExecutionService;
    @Autowired private TaskCronScheduler taskCronScheduler;
    @Autowired private AuditLogService auditLogService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tasks", opsTaskRepository.findAll());
        return "tasks/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("task", new OpsTask());
        model.addAttribute("assets", assetRepository.findAll());
        return "tasks/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("task", opsTaskRepository.findById(id).orElseThrow());
        model.addAttribute("assets", assetRepository.findAll());
        return "tasks/form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public String save(@ModelAttribute OpsTask task) {
        OpsTask saved = opsTaskRepository.save(task);
        taskCronScheduler.reschedule(saved);
        auditLogService.log("TASK_SAVE", "OpsTask", saved.getName(), "cron=" + saved.getCronExpression());
        return "redirect:/tasks";
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> delete(@PathVariable Long id) {
        taskCronScheduler.cancel(id);
        opsTaskRepository.deleteById(id);
        auditLogService.log("TASK_DELETE", "OpsTask", String.valueOf(id), null);
        return Map.of("success", true);
    }

    /** 立即手动执行一次（异步下发到所有目标资产） */
    @PostMapping("/{id}/run")
    @ResponseBody
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Map<String, Object> run(@PathVariable Long id) {
        taskExecutionService.executeTask(id);
        auditLogService.log("TASK_RUN", "OpsTask", String.valueOf(id), "手动触发执行");
        return Map.of("success", true, "message", "任务已提交执行，请稍后查看执行记录");
    }

    @GetMapping("/{id}/executions")
    public String executions(@PathVariable Long id, Model model) {
        model.addAttribute("task", opsTaskRepository.findById(id).orElseThrow());
        model.addAttribute("executions", taskExecutionRepository.findByTaskIdOrderByStartTimeDesc(id));
        return "tasks/executions";
    }

    @GetMapping("/executions/all")
    public String allExecutions(Model model) {
        model.addAttribute("executions", taskExecutionRepository.findTop100ByOrderByStartTimeDesc());
        return "tasks/executions-all";
    }
}
