package com.ops.platform.config;

import com.ops.platform.entity.SysUser;
import com.ops.platform.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 应用首次启动时初始化默认管理员账号：admin / Admin@123
 * 强烈建议登录后立即在"系统设置"中修改初始密码。
 */
@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private SysUserRepository sysUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (sysUserRepository.findByUsername("admin").isEmpty()) {
            SysUser admin = new SysUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            sysUserRepository.save(admin);
        }
    }
}
