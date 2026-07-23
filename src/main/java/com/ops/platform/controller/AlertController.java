package com.ops.platform.controller;

import com.ops.platform.entity.AlertRecord;
import com.ops.platform.entity.AlertRule;
import com.ops.platform.repository.AlertRecordRepository;
import com.ops.platform.repository.AlertRuleRepository;
import com.ops.platform.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/alerts")
public class AlertController {

    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private AlertRecordRepository alertRecordRepository;
    @Autowired private AuditLogService auditLogService;

    @GetMapping("/rules")
    public String rules(Model model) {
        model.addAttribute("rules", alertRuleRepository.findAll());
        return "alerts/rules";
    }

    @GetMapping("/rules/new")
    public String newRule(Model model) {
        model.addAttribute("rule", new AlertRule());
        return "alerts/rule-form";
    }

    @GetMapping("/rules/{id}/edit")
    public String editRule(@PathVariable Long id, Model model) {
        model.addAttribute("rule", alertRuleRepository.findById(id).orElseThrow());
        return "alerts/rule-form";
    }

    @PostMapping("/rules/save")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public String saveRule(@ModelAttribute AlertRule rule) {
        alertRuleRepository.save(rule);
        auditLogService.log("RULE_SAVE", "AlertRule", rule.getName(), rule.getMetricType() + rule.getOperator() + rule.getThreshold());
        return "redirect:/alerts/rules";
    }

    @PostMapping("/rules/{id}/delete")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> deleteRule(@PathVariable Long id) {
        alertRuleRepository.deleteById(id);
        auditLogService.log("RULE_DELETE", "AlertRule", String.valueOf(id), null);
        return Map.of("success", true);
    }

    @GetMapping("/records")
    public String records(@RequestParam(required = false, defaultValue = "OPEN") String status, Model model) {
        model.addAttribute("records", "ALL".equals(status) ? alertRecordRepository.findTop50ByOrderByCreateTimeDesc()
                : alertRecordRepository.findByStatusOrderByCreateTimeDesc(status));
        model.addAttribute("currentStatus", status);
        return "alerts/records";
    }

    /** 人工确认告警（不代表已恢复，仅标记已知晓，便于团队协作；不影响自动恢复判定） */
    @PostMapping("/records/{id}/ack")
    @ResponseBody
    public Map<String, Object> ack(@PathVariable Long id) {
        AlertRecord r = alertRecordRepository.findById(id).orElseThrow();
        r.setAcked(true);
        alertRecordRepository.save(r);
        return Map.of("success", true);
    }
}
