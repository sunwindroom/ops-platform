package com.ops.platform.controller;

import com.ops.platform.entity.Asset;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.service.AuditLogService;
import com.ops.platform.service.DiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 网络自动发现：扫描网段，识别存活主机与网络设备，供用户勾选批量导入（参考 LibreNMS/PRTG 自动发现理念） */
@Controller
@RequestMapping("/discovery")
public class DiscoveryController {

    @Autowired private DiscoveryService discoveryService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AuditLogService auditLogService;

    @GetMapping
    public String page() {
        return "discovery";
    }

    @PostMapping("/scan")
    @ResponseBody
    public List<DiscoveryService.DiscoveryResult> scan(@RequestParam String cidr,
                                                         @RequestParam(required = false, defaultValue = "public") String community) {
        return discoveryService.scan(cidr, community);
    }

    /** 将勾选的发现结果批量导入为资产（仅登记IP和推测类别，账号密码需用户后续在资产管理中补充） */
    @PostMapping("/import")
    @ResponseBody
    public Map<String, Object> importAssets(@RequestBody List<Map<String, String>> items) {
        int count = 0;
        List<Asset> existing = assetRepository.findAll();
        for (Map<String, String> item : items) {
            String ip = item.get("ip");
            if (ip == null || ip.isBlank()) continue;
            boolean exists = existing.stream().anyMatch(a -> a.getIp().equals(ip));
            if (exists) continue; // 已存在相同IP的资产则跳过，避免重复导入
            Asset asset = new Asset();
            asset.setName(item.getOrDefault("name", ip));
            asset.setIp(ip);
            String category = item.getOrDefault("category", "physical_server");
            asset.setCategory(category);
            if ("switch".equals(category) || "router".equals(category) || "audit_gateway".equals(category)) {
                asset.setType("NETWORK");
                asset.setCollectMethod("SNMP");
            } else if ("esxi_host".equals(category)) {
                asset.setType("ESXI");
                asset.setCollectMethod("SSH");
            } else {
                asset.setType("PHYSICAL");
                asset.setCollectMethod("SSH");
            }
            asset.setMonitorEnabled(false); // 默认先停用采集，待补充账号密码后手动启用，避免立即产生大量离线告警
            asset.setRemark("网络自动发现导入，请补充采集账号信息后启用监控");
            assetRepository.save(asset);
            count++;
        }
        auditLogService.log("DISCOVERY_IMPORT", "Asset", null, "批量导入 " + count + " 个资产");
        return Map.of("success", true, "imported", count);
    }
}
