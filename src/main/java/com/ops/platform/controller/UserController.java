package com.ops.platform.controller;

import com.ops.platform.entity.SysUser;
import com.ops.platform.repository.SysUserRepository;
import com.ops.platform.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 用户与权限管理，仅ADMIN角色可访问（RBAC：ADMIN全权 / OPERATOR可操作资产任务但不可管理用户 / VIEWER只读） */
@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    @Autowired private SysUserRepository sysUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditLogService auditLogService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", sysUserRepository.findAll());
        return "users/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("user", new SysUser());
        return "users/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute SysUser user, @RequestParam(required = false) String rawPassword) {
        if (user.getId() == null) {
            user.setPassword(passwordEncoder.encode(rawPassword == null || rawPassword.isBlank() ? "Ops@123456" : rawPassword));
        } else if (rawPassword != null && !rawPassword.isBlank()) {
            user.setPassword(passwordEncoder.encode(rawPassword));
        } else {
            sysUserRepository.findById(user.getId()).ifPresent(old -> user.setPassword(old.getPassword()));
        }
        sysUserRepository.save(user);
        auditLogService.log("USER_SAVE", "SysUser", user.getUsername(), "角色=" + user.getRole());
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    @ResponseBody
    public Map<String, Object> delete(@PathVariable Long id) {
        sysUserRepository.findById(id).ifPresent(u -> {
            if (!"admin".equals(u.getUsername())) {
                sysUserRepository.deleteById(id);
                auditLogService.log("USER_DELETE", "SysUser", u.getUsername(), null);
            }
        });
        return Map.of("success", true);
    }
}
