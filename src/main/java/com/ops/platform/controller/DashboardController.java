package com.ops.platform.controller;

import com.ops.platform.entity.Asset;
import com.ops.platform.entity.MetricData;
import com.ops.platform.repository.AlertRecordRepository;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.repository.MetricDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class DashboardController {

    @Autowired private AssetRepository assetRepository;
    @Autowired private AlertRecordRepository alertRecordRepository;
    @Autowired private MetricDataRepository metricDataRepository;

    @GetMapping("/")
    public String index(Model model) {
        List<Asset> all = assetRepository.findAll();
        Map<String, Long> typeCount = all.stream()
                .collect(Collectors.groupingBy(Asset::getType, Collectors.counting()));
        long online = all.stream().filter(a -> "ONLINE".equals(a.getStatus())).count();
        long offline = all.stream().filter(a -> "OFFLINE".equals(a.getStatus())).count();

        model.addAttribute("totalAssets", all.size());
        model.addAttribute("onlineCount", online);
        model.addAttribute("offlineCount", offline);
        model.addAttribute("typeCount", typeCount);
        model.addAttribute("openAlertCount", alertRecordRepository.countByStatus("OPEN"));
        model.addAttribute("recentAlerts", alertRecordRepository.findTop50ByOrderByCreateTimeDesc()
                .stream().limit(10).toList());

        // Top5 CPU / 内存 使用率资产（参考商业监控大盘常见的"资源占用TopN"看板）
        model.addAttribute("topCpu", topN(all, "cpu_usage", 5));
        model.addAttribute("topMem", topN(all, "mem_usage", 5));

        return "dashboard";
    }

    private List<Map<String, Object>> topN(List<Asset> assets, String metricType, int n) {
        return assets.stream()
                .map(a -> {
                    MetricData md = metricDataRepository.findFirstByAssetIdAndMetricTypeOrderByCollectTimeDesc(a.getId(), metricType);
                    if (md == null) return null;
                    return Map.<String, Object>of("name", a.getName(), "ip", a.getIp(), "value", md.getValue());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(m -> -(Double) m.get("value")))
                .limit(n)
                .toList();
    }
}
