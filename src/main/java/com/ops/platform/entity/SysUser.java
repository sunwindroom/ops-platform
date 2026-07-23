package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** BCrypt加密后的密码 */
    @Column(nullable = false)
    private String password;

    /** ADMIN / OPERATOR / VIEWER */
    @Column(length = 20)
    private String role = "ADMIN";

    private Boolean enabled = true;
}
