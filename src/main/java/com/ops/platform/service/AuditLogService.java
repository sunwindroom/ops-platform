package com.ops.platform.service;

import com.ops.platform.entity.AuditLog;
import com.ops.platform.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    public void log(String action, String targetType, String targetId, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUsername(currentUsername());
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setDetail(detail);
            auditLogRepository.save(log);
        } catch (Exception ignored) {
            // 审计日志失败不应影响主流程
        }
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }
}
