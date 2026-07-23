package com.ops.platform.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 资产表：统一存放物理服务器、虚拟服务器(VM/ESXi宿主)、网络设备(交换机/路由器/上网行为审计)
 */
@Data
@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 资产名称，如 web-server-01 */
    @Column(nullable = false, length = 100)
    private String name;

    /** 管理IP */
    @Column(nullable = false, length = 50)
    private String ip;

    /** 大类：PHYSICAL(物理服务器) / VIRTUAL(虚拟机) / ESXI(虚拟化宿主) / NETWORK(网络设备) */
    @Column(nullable = false, length = 20)
    private String type;

    /** 细分类别：physical_server, vm_linux, vm_windows, esxi_host, switch, router, audit_gateway 等 */
    @Column(length = 30)
    private String category;

    /** 操作系统/固件信息 */
    private String osInfo;

    /** 厂商/型号，如 华为S5720、H3C */
    private String vendor;

    /** 所属机房/位置 */
    private String location;

    /** 采集方式：SSH / SNMP / NONE(仅登记不采集) */
    @Column(length = 20)
    private String collectMethod = "SSH";

    /** ---- SSH 采集/自动化下发所需（用于Linux、Windows-OpenSSH、ESXi） ---- */
    private Integer sshPort = 22;
    private String sshUser;
    /** AES加密后存储 */
    private String sshPasswordEnc;
    /** 是否使用密钥登录 */
    private Boolean sshUseKey = false;
    @Column(columnDefinition = "TEXT")
    private String sshPrivateKeyEnc;

    /** ---- SNMP 采集所需（用于交换机/路由器/审计设备） ---- */
    private Integer snmpPort = 161;
    private String snmpCommunity = "public";
    private String snmpVersion = "v2c";

    /** 厂商私有MIB的CPU/内存OID（不同厂商不同，可在资产编辑页填写，留空则不采集CPU/内存，仅采集接口流量与在线状态）
     * 常见示例：
     *  Huawei CPU 5分钟平均: 1.3.6.1.4.1.2011.6.3.4.1.4.10
     *  H3C CPU 5分钟平均: 1.3.6.1.4.1.25506.2.6.1.1.1.1.6
     *  Cisco CPU 5分钟平均: 1.3.6.1.4.1.9.9.109.1.1.1.1.5
     */
    private String snmpOidCpu;
    private String snmpOidMem;

    /** 监控的接口索引（ifIndex），默认1（通常为主上行口），可在编辑页按需修改为其他接口编号 */
    private Integer snmpIfIndex = 1;

    /** ---- 探针监控参数（用于 collectMethod=PROBE 的资产，如网站/业务端口存活检测，参考PRTG Sensor理念） ---- */
    /** TCP / HTTP / PING */
    private String probeType;
    private Integer probePort;
    private String probeUrl;
    private Integer probeTimeoutMs = 3000;

    /** 关联的ESXi宿主ID（若本资产是虚拟机） */
    private Long parentHostId;

    /** 状态：ONLINE/OFFLINE/UNKNOWN */
    @Column(length = 20)
    private String status = "UNKNOWN";

    /** 是否启用采集 */
    private Boolean monitorEnabled = true;

    private String remark;

    private LocalDateTime createTime = LocalDateTime.now();
    private LocalDateTime updateTime = LocalDateTime.now();
}
