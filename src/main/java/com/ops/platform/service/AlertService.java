package com.ops.platform.service;

import com.ops.platform.entity.*;
import com.ops.platform.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class AlertService {

    @Autowired private AlertRuleRepository alertRuleRepository;
    @Autowired private AlertRecordRepository alertRecordRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private MetricDataRepository metricDataRepository;
    @Autowired private AlertMuteRepository alertMuteRepository;
    @Autowired private NotificationService notificationService;

    /** 遍历所有启用规则，评估是否触发/恢复告警（由定时任务周期调用） */
    public void evaluateAll() {
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();
        for (AlertRule rule : rules) {
            try {
                evaluateRule(rule);
            } catch (Exception e) {
                log.error("告警规则评估异常 ruleId={}", rule.getId(), e);
            }
        }
        checkEscalations();
    }

    private void evaluateRule(AlertRule rule) {
        List<Asset> targets;
        if (rule.getAssetId() != null) {
            Asset a = assetRepository.findById(rule.getAssetId()).orElse(null);
            targets = a == null ? List.of() : List.of(a);
        } else if ("ALL".equals(rule.getAssetType())) {
            targets = assetRepository.findAll();
        } else {
            targets = assetRepository.findByType(rule.getAssetType());
        }

        for (Asset asset : targets) {
            if (!Boolean.TRUE.equals(asset.getMonitorEnabled())) continue;

            List<MetricData> recent = metricDataRepository
                    .findTop5ByAssetIdAndMetricTypeOrderByCollectTimeDesc(asset.getId(), rule.getMetricType());
            if (recent.isEmpty()) continue;

            int needTimes = rule.getConsecutiveTimes() == null || rule.getConsecutiveTimes() < 1 ? 1 : rule.getConsecutiveTimes();
            boolean allBreach = recent.size() >= needTimes;
            for (int i = 0; i < needTimes && i < recent.size(); i++) {
                if (!breach(rule, recent.get(i).getValue())) {
                    allBreach = false;
                    break;
                }
            }

            AlertRecord open = alertRecordRepository
                    .findFirstByRuleIdAndAssetIdAndStatusOrderByCreateTimeDesc(rule.getId(), asset.getId(), "OPEN");

            if (allBreach) {
                if (open == null) {
                    // 新触发：先检查是否处于静默/维护窗口，静默期间不产生通知（但仍登记内部状态，便于事后追溯）
                    boolean muted = isMuted(asset.getId());

                    AlertRecord record = new AlertRecord();
                    record.setRuleId(rule.getId());
                    record.setAssetId(asset.getId());
                    record.setAssetName(asset.getName());
                    record.setMetricType(rule.getMetricType());
                    record.setTriggerValue(recent.get(0).getValue());
                    record.setThreshold(rule.getThreshold());
                    record.setLevel(rule.getLevel());
                    record.setStatus("OPEN");
                    record.setMessage(String.format("%s %s %.2f (阈值 %s %.2f)%s",
                            asset.getName(), rule.getMetricType(), recent.get(0).getValue(),
                            rule.getOperator(), rule.getThreshold(), muted ? "  [维护窗口静默中]" : ""));
                    alertRecordRepository.save(record);

                    if (!muted) {
                        notificationService.sendAlert(record);
                    }
                }
                // 已有OPEN告警则不重复通知，避免告警轰炸（超时未处理走 checkEscalations 升级逻辑）
            } else {
                if (open != null) {
                    open.setStatus("RESOLVED");
                    open.setResolveTime(LocalDateTime.now());
                    alertRecordRepository.save(open);
                    if (!isMuted(asset.getId())) {
                        notificationService.sendRecoverNotice(open);
                    }
                }
            }
        }
    }

    /**
     * 告警升级（参考 Zabbix "N分钟未处理自动升级"理念）：
     * 对仍处于OPEN状态、规则配置了 escalateMinutes 且尚未升级过的告警，
     * 若持续时间超过阈值，则提升级别并重新强提醒，避免长期未处理的告警被淹没。
     */
    private void checkEscalations() {
        List<AlertRecord> openAlerts = alertRecordRepository.findByStatusOrderByCreateTimeDesc("OPEN");
        for (AlertRecord record : openAlerts) {
            if (Boolean.TRUE.equals(record.getEscalated()) || record.getRuleId() == null) continue;
            AlertRule rule = alertRuleRepository.findById(record.getRuleId()).orElse(null);
            if (rule == null || rule.getEscalateMinutes() == null || rule.getEscalateMinutes() <= 0) continue;

            long minutes = Duration.between(record.getCreateTime(), LocalDateTime.now()).toMinutes();
            if (minutes >= rule.getEscalateMinutes()) {
                record.setLevel(rule.getEscalateToLevel() == null ? "critical" : rule.getEscalateToLevel());
                record.setEscalated(true);
                record.setMessage(record.getMessage() + String.format("  [已升级：超过%d分钟未处理]", rule.getEscalateMinutes()));
                alertRecordRepository.save(record);
                if (!isMuted(record.getAssetId())) {
                    notificationService.sendAlert(record);
                }
            }
        }
    }

    private boolean isMuted(Long assetId) {
        LocalDateTime now = LocalDateTime.now();
        List<AlertMute> mutes = alertMuteRepository.findByEndTimeAfter(now);
        for (AlertMute m : mutes) {
            if (now.isBefore(m.getStartTime())) continue;
            if (m.getAssetId() == null || m.getAssetId().equals(assetId)) return true;
        }
        return false;
    }

    private boolean breach(AlertRule rule, double value) {
        double t = rule.getThreshold();
        return switch (rule.getOperator()) {
            case ">" -> value > t;
            case ">=" -> value >= t;
            case "<" -> value < t;
            case "<=" -> value <= t;
            case "==" -> value == t;
            default -> false;
        };
    }
}
