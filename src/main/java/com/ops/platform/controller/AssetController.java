package com.ops.platform.controller;

import com.ops.platform.entity.Asset;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.service.AssetService;
import com.ops.platform.service.AuditLogService;
import com.ops.platform.service.CollectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/assets")
public class AssetController {

    @Autowired private AssetService assetService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private CollectService collectService;
    @Autowired private AuditLogService auditLogService;

    /** 资产列表页，可按大类筛选：全部/物理服务器/虚拟机/虚拟化宿主/网络设备/探针 */
    @GetMapping
    public String list(@RequestParam(required = false) String type, Model model) {
        List<Asset> assets = (type == null || type.isBlank()) ? assetService.findAll() : assetService.findByType(type);
        model.addAttribute("assets", assets);
        model.addAttribute("currentType", type);
        return "assets/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("asset", new Asset());
        return "assets/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("asset", assetService.findById(id).orElseThrow());
        return "assets/form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public String save(@ModelAttribute Asset asset,
                        @RequestParam(required = false) String rawSshPassword,
                        @RequestParam(required = false) String rawSshPrivateKey) {
        boolean isNew = asset.getId() == null;
        assetService.save(asset, rawSshPassword, rawSshPrivateKey);
        auditLogService.log(isNew ? "ASSET_CREATE" : "ASSET_UPDATE", "Asset", asset.getName(), asset.getIp());
        return "redirect:/assets";
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> delete(@PathVariable Long id) {
        assetService.findById(id).ifPresent(a -> auditLogService.log("ASSET_DELETE", "Asset", a.getName(), a.getIp()));
        assetService.delete(id);
        return Map.of("success", true);
    }

    /** 手动触发一次立即采集，方便新增资产后马上验证连通性 */
    @PostMapping("/{id}/collect-now")
    @ResponseBody
    public Map<String, Object> collectNow(@PathVariable Long id) {
        Asset asset = assetRepository.findById(id).orElseThrow();
        collectService.collectOne(asset);
        Asset refreshed = assetRepository.findById(id).orElseThrow();
        return Map.of("success", true, "status", refreshed.getStatus());
    }

    @GetMapping("/import")
    public String importPage() {
        return "assets/import";
    }

    /**
     * CSV批量导入资产。表头需为：name,ip,type,category,collectMethod,sshPort,sshUser,rawSshPassword,snmpCommunity
     * 未填写的列使用默认值；密码列同样会经AES加密后存储。
     */
    @PostMapping("/import")
    @ResponseBody
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Map<String, Object> importCsv(@RequestParam("file") MultipartFile file) {
        int success = 0, fail = 0;
        List<String> errors = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) return Map.of("success", false, "message", "文件为空");
            String[] cols = header.split(",");
            String line;
            int rowNum = 1;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                if (line.isBlank()) continue;
                String[] vals = line.split(",", -1);
                try {
                    Asset asset = new Asset();
                    String rawPassword = null;
                    for (int i = 0; i < cols.length && i < vals.length; i++) {
                        String col = cols[i].trim();
                        String val = vals[i].trim();
                        switch (col) {
                            case "name" -> asset.setName(val);
                            case "ip" -> asset.setIp(val);
                            case "type" -> asset.setType(val.isBlank() ? "PHYSICAL" : val);
                            case "category" -> asset.setCategory(val.isBlank() ? "physical_server" : val);
                            case "collectMethod" -> asset.setCollectMethod(val.isBlank() ? "SSH" : val);
                            case "sshPort" -> { if (!val.isBlank()) asset.setSshPort(Integer.parseInt(val)); }
                            case "sshUser" -> asset.setSshUser(val);
                            case "rawSshPassword" -> rawPassword = val;
                            case "snmpCommunity" -> { if (!val.isBlank()) asset.setSnmpCommunity(val); }
                            default -> { /* 忽略未识别列 */ }
                        }
                    }
                    if (asset.getName() == null || asset.getName().isBlank() || asset.getIp() == null || asset.getIp().isBlank()) {
                        errors.add("第" + rowNum + "行：name/ip不能为空");
                        fail++;
                        continue;
                    }
                    assetService.save(asset, rawPassword, null);
                    success++;
                } catch (Exception e) {
                    errors.add("第" + rowNum + "行：" + e.getMessage());
                    fail++;
                }
            }
        } catch (Exception e) {
            return Map.of("success", false, "message", "文件解析失败: " + e.getMessage());
        }
        auditLogService.log("ASSET_IMPORT_CSV", "Asset", null, "成功" + success + "条，失败" + fail + "条");
        return Map.of("success", true, "imported", success, "failed", fail, "errors", errors);
    }
}
