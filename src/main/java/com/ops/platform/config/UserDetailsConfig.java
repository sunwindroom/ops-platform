package com.ops.platform.config;

import com.ops.platform.repository.SysUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Configuration
public class UserDetailsConfig {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            com.ops.platform.entity.SysUser u = sysUserRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
            return User.withUsername(u.getUsername())
                    .password(u.getPassword())
                    .roles(u.getRole())
                    .disabled(!Boolean.TRUE.equals(u.getEnabled()))
                    .build();
        };
    }
}
