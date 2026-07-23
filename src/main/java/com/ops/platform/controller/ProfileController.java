package com.ops.platform.controller;

import com.ops.platform.entity.SysUser;
import com.ops.platform.repository.SysUserRepository;
import com.ops.platform.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/profile")
public class ProfileController {

    @Autowired private SysUserRepository sysUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogService auditLogService;

    @GetMapping
    public String profile(Model model, Authentication auth) {
        model.addAttribute("user", sysUserRepository.findByUsername(auth.getName()).orElseThrow());
        return "profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                  @RequestParam String newPassword,
                                  @RequestParam String confirmPassword,
                                  Authentication auth, Model model) {
        SysUser user = sysUserRepository.findByUsername(auth.getName()).orElseThrow();
        model.addAttribute("user", user);

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            model.addAttribute("error", "原密码不正确");
            return "profile";
        }
        if (newPassword == null || newPassword.length() < 6) {
            model.addAttribute("error", "新密码长度至少6位");
            return "profile";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "两次输入的新密码不一致");
            return "profile";
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        sysUserRepository.save(user);
        auditLogService.log("CHANGE_PASSWORD", "SysUser", user.getUsername(), "用户自助修改密码");
        model.addAttribute("success", "密码修改成功，下次登录请使用新密码");
        return "profile";
    }
}
