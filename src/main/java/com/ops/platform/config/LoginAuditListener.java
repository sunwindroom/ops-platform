package com.ops.platform.config;

import com.ops.platform.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

@Component
public class LoginAuditListener {

    @Autowired
    private AuditLogService auditLogService;

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        auditLogService.log("LOGIN", "SysUser", event.getAuthentication().getName(), "登录成功");
    }
}
