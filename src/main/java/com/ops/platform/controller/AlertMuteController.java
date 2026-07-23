package com.ops.platform.controller;

import com.ops.platform.entity.AlertMute;
import com.ops.platform.repository.AlertMuteRepository;
import com.ops.platform.repository.AssetRepository;
import com.ops.platform.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 告警静默/维护窗口管理（参考 Prometheus Alertmanager Silence） */
@Controller
@RequestMapping("/alerts/mutes")
public class AlertMuteController {

    @Autowired private AlertMuteRepository alertMuteRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AuditLogService auditLogService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("mutes", alertMuteRepository.findAll());
        model.addAttribute("assets", assetRepository.findAll());
        return "alerts/mutes";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute AlertMute mute, Authentication auth) {
        if (mute.getAssetId() != null) {
            assetRepository.findById(mute.getAssetId()).ifPresent(a -> mute.setAssetName(a.getName()));
        } else {
            mute.setAssetName("全部资产");
        }
        mute.setCreatedBy(auth != null ? auth.getName() : "system");
        alertMuteRepository.save(mute);
        auditLogService.log("MUTE_CREATE", "AlertMute", String.valueOf(mute.getId()),
                mute.getAssetName() + " " + mute.getStartTime() + " ~ " + mute.getEndTime());
        return "redirect:/alerts/mutes";
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable Long id) {
        alertMuteRepository.deleteById(id);
        auditLogService.log("MUTE_DELETE", "AlertMute", String.valueOf(id), null);
        return Map.of("success", true);
    }
}
