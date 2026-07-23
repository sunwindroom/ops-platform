package com.ops.platform.service;

import com.ops.platform.entity.Asset;
import com.ops.platform.entity.MetricData;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.repository.MetricDataRepository;
import com.ops.platform.service.collector.ProbeCollector;
import com.ops.platform.service.collector.SnmpCollector;
import com.ops.platform.service.collector.SshCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CollectService {

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private MetricDataRepository metricDataRepository;
    @Autowired
    private SshCollector sshCollector;
    @Autowired
    private SnmpCollector snmpCollector;
    @Autowired
    private ProbeCollector probeCollector;

    /** 对单个资产执行一次采集，落库，并更新资产在线状态 */
    public void collectOne(Asset asset) {
        if (!Boolean.TRUE.equals(asset.getMonitorEnabled())) return;

        Map<String, Double> metrics;
        try {
            if ("PROBE".equals(asset.getCollectMethod())) {
                metrics = probeCollector.collect(asset);
            } else {
                switch (asset.getCategory() == null ? "" : asset.getCategory()) {
                    case "vm_windows":
                    case "physical_windows":
                        metrics = sshCollector.collectWindows(asset);
                        break;
                    case "esxi_host":
                        metrics = sshCollector.collectEsxi(asset);
                        break;
                    case "switch":
                    case "router":
                    case "audit_gateway":
                        metrics = snmpCollector.collect(asset);
                        break;
                    default: // vm_linux / physical_server 等默认走Linux SSH采集
                        metrics = sshCollector.collectLinux(asset);
                }
            }
        } catch (Exception e) {
            log.warn("采集资产[{}]失败: {}", asset.getName(), e.getMessage());
            markOffline(asset);
            return;
        }

        boolean success = metrics != null && !metrics.containsKey("_error")
                && (!metrics.containsKey("online") || metrics.get("online") == 1.0);

        LocalDateTime now = LocalDateTime.now();

        if (metrics != null) {
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                String type = entry.getKey();
                if (type.equals("_error") || type.equals("online")) continue;

                if (type.equals("if_in_octets") || type.equals("if_out_octets")) {
                    saveRateMetric(asset, type, entry.getValue(), now);
                    continue;
                }
                saveMetric(asset, type, entry.getValue(), now);
            }
        }

        // 统一落库 online 指标（不论具体采集方式），使基于"online"的告警规则可以对任意资产类型生效
        saveMetric(asset, "online", success ? 1.0 : 0.0, now);

        if (success) {
            markOnline(asset);
        } else {
            markOffline(asset);
        }
    }

    /** 对所有启用监控的资产批量采集（供定时任务调用） */
    public void collectAll() {
        List<Asset> assets = assetRepository.findByMonitorEnabledTrue();
        for (Asset asset : assets) {
            try {
                collectOne(asset);
            } catch (Exception e) {
                log.error("采集异常 asset={}", asset.getId(), e);
            }
        }
    }

    private void saveMetric(Asset asset, String type, Double value, LocalDateTime time) {
        MetricData md = new MetricData();
        md.setAssetId(asset.getId());
        md.setMetricType(type);
        md.setValue(value);
        md.setCollectTime(time);
        metricDataRepository.save(md);
    }

    /** 针对累计计数器类指标（网卡字节数），换算成 kbps 速率后再存储 */
    private void saveRateMetric(Asset asset, String counterType, Double newValue, LocalDateTime now) {
        String rateType = counterType.equals("if_in_octets") ? "net_in_kbps" : "net_out_kbps";
        MetricData prev = metricDataRepository.findFirstByAssetIdAndMetricTypeOrderByCollectTimeDesc(asset.getId(), counterType);

        // 先保存原始计数器值，供下次计算差值
        saveMetric(asset, counterType, newValue, now);

        if (prev == null) return;
        double seconds = Duration.between(prev.getCollectTime(), now).getSeconds();
        if (seconds <= 0) return;
        double delta = newValue - prev.getValue();
        if (delta < 0) return; // 计数器翻转或设备重启，忽略本次
        double kbps = (delta * 8.0 / 1000.0) / seconds;
        saveMetric(asset, rateType, Math.round(kbps * 100.0) / 100.0, now);
    }

    private void markOnline(Asset asset) {
        if (!"ONLINE".equals(asset.getStatus())) {
            asset.setStatus("ONLINE");
            asset.setUpdateTime(LocalDateTime.now());
            assetRepository.save(asset);
        }
    }

    private void markOffline(Asset asset) {
        if (!"OFFLINE".equals(asset.getStatus())) {
            asset.setStatus("OFFLINE");
            asset.setUpdateTime(LocalDateTime.now());
            assetRepository.save(asset);
        }
    }
}
