package com.ops.platform.service;

import com.ops.platform.service.collector.SnmpCollector;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * 网络自动发现服务（参考 LibreNMS/PRTG 的网段扫描理念）：
 * 对给定网段做存活探测，并尝试SNMP识别设备信息，供用户在页面上勾选后批量导入为资产，
 * 避免逐台手工录入IP/账号信息。
 */
@Service
public class DiscoveryService {

    @Autowired
    private SnmpCollector snmpCollector;

    private static final int MAX_HOSTS = 512; // 单次扫描上限，避免误填过大网段导致长时间阻塞

    @Data
    public static class DiscoveryResult {
        private String ip;
        private boolean reachable;
        private boolean snmpSupported;
        private String sysDescr;
        private String suggestCategory; // 猜测的资产类别，仅作参考
    }

    /**
     * 扫描给定CIDR网段（如 192.168.1.0/24），返回存活主机列表。
     * 存活判定：ICMP优先，失败则回退常见TCP端口(22/80/443/161/3389)连通性判断。
     */
    public List<DiscoveryResult> scan(String cidr, String snmpCommunity) {
        List<String> ips = expandCidr(cidr);
        if (ips.size() > MAX_HOSTS) {
            ips = ips.subList(0, MAX_HOSTS);
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(64, Math.max(8, ips.size())));
        List<Future<DiscoveryResult>> futures = new ArrayList<>();
        for (String ip : ips) {
            futures.add(pool.submit(() -> probe(ip, snmpCommunity)));
        }
        List<DiscoveryResult> results = new ArrayList<>();
        for (Future<DiscoveryResult> f : futures) {
            try {
                DiscoveryResult r = f.get(5, TimeUnit.SECONDS);
                if (r.isReachable()) results.add(r);
            } catch (Exception ignored) {
            }
        }
        pool.shutdownNow();
        return results;
    }

    private DiscoveryResult probe(String ip, String community) {
        DiscoveryResult r = new DiscoveryResult();
        r.setIp(ip);
        r.setReachable(isReachable(ip));
        if (r.isReachable()) {
            String descr = snmpCollector.getSysDescr(ip, community, 161);
            if (descr != null && !descr.isBlank()) {
                r.setSnmpSupported(true);
                r.setSysDescr(descr);
                r.setSuggestCategory(guessCategory(descr));
            } else {
                r.setSuggestCategory("physical_server"); // 无SNMP响应，默认按服务器猜测，用户可自行修改
            }
        }
        return r;
    }

    private boolean isReachable(String ip) {
        try {
            if (InetAddress.getByName(ip).isReachable(800)) return true;
        } catch (Exception ignored) {
        }
        for (int port : new int[]{22, 80, 443, 161, 3389}) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 500);
                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private String guessCategory(String sysDescr) {
        String d = sysDescr.toLowerCase();
        if (d.contains("cisco") || d.contains("huawei") || d.contains("h3c") || d.contains("switch") || d.contains("router")) {
            return d.contains("router") ? "router" : "switch";
        }
        if (d.contains("windows")) return "physical_windows";
        if (d.contains("linux") || d.contains("vmware") || d.contains("esxi")) {
            return d.contains("esxi") ? "esxi_host" : "physical_server";
        }
        return "switch"; // SNMP能通常意味着是网络设备
    }

    /** 简单展开 CIDR（仅支持常见的 /24~/30，足够覆盖内网典型场景） */
    private List<String> expandCidr(String cidr) {
        List<String> result = new ArrayList<>();
        try {
            String[] parts = cidr.trim().split("/");
            String base = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;
            String[] octets = base.split("\\.");
            long ip = 0;
            for (String o : octets) ip = (ip << 8) + Integer.parseInt(o);
            int hostBits = 32 - prefix;
            long count = hostBits <= 0 ? 1 : (1L << hostBits);
            long network = (ip >> hostBits) << hostBits;
            for (long i = 0; i < count; i++) {
                long cur = network + i;
                if (hostBits >= 8 && (i == 0 || i == count - 1)) continue; // 跳过网络地址/广播地址
                result.add(String.format("%d.%d.%d.%d",
                        (cur >> 24) & 0xFF, (cur >> 16) & 0xFF, (cur >> 8) & 0xFF, cur & 0xFF));
            }
        } catch (Exception e) {
            // 非法输入直接返回空列表，前端会提示"未发现设备"
        }
        return result;
    }
}
