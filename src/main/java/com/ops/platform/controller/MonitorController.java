package com.ops.platform.controller;

import com.ops.platform.entity.Asset;
import com.ops.platform.entity.MetricData;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.repository.MetricDataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
public class MonitorController {

    @Autowired private AssetRepository assetRepository;
    @Autowired private MetricDataRepository metricDataRepository;

    @GetMapping("/monitor/{id}")
    public String detail(@PathVariable Long id, Model model) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        model.addAttribute("asset", asset);
        return "monitor/detail";
    }

    /** 供页面Chart.js拉取指定资产、指定指标近N小时的历史数据 */
    @GetMapping("/api/metrics/{assetId}")
    @ResponseBody
    public Map<String, Object> history(@PathVariable Long assetId,
                                        @RequestParam String metricType,
                                        @RequestParam(defaultValue = "6") int hours) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(hours);
        List<MetricData> data = metricDataRepository
                .findByAssetIdAndMetricTypeAndCollectTimeBetweenOrderByCollectTimeAsc(assetId, metricType, start, end);
        List<String> labels = data.stream().map(d -> d.getCollectTime().toString()).toList();
        List<Double> values = data.stream().map(MetricData::getValue).toList();
        return Map.of("labels", labels, "values", values);
    }

    /** 获取某资产最新一批关键指标，用于详情页顶部卡片展示 */
    @GetMapping("/api/metrics/{assetId}/latest")
    @ResponseBody
    public Map<String, Object> latest(@PathVariable Long assetId) {
        String[] types = {"cpu_usage", "mem_usage", "disk_usage", "net_in_kbps", "net_out_kbps", "load1", "if_status",
                "response_ms", "http_status"};
        Map<String, Object> result = new java.util.HashMap<>();
        for (String t : types) {
            MetricData md = metricDataRepository.findFirstByAssetIdAndMetricTypeOrderByCollectTimeDesc(assetId, t);
            if (md != null) result.put(t, md.getValue());
        }
        return result;
    }
}
