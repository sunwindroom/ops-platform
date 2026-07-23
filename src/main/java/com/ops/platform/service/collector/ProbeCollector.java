package com.ops.platform.service.collector;

import com.ops.platform.entity.Asset;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 探针监控采集器（参考 PRTG Sensor / Uptime Kuma 的探针理念）：
 * 不依赖SSH/SNMP账号，直接对目标做 TCP端口连通性 / HTTP服务存活 / ICMP Ping 探测，
 * 适合业务端口存活检测、网站可用性监控等轻量场景。
 */
@Component
public class ProbeCollector {

    public Map<String, Double> collect(Asset asset) {
        String type = asset.getProbeType() == null ? "TCP" : asset.getProbeType().toUpperCase();
        return switch (type) {
            case "HTTP" -> collectHttp(asset);
            case "PING" -> collectPing(asset);
            default -> collectTcp(asset);
        };
    }

    private Map<String, Double> collectTcp(Asset asset) {
        Map<String, Double> result = new HashMap<>();
        int port = asset.getProbePort() == null ? 80 : asset.getProbePort();
        int timeout = asset.getProbeTimeoutMs() == null ? 3000 : asset.getProbeTimeoutMs();
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(asset.getIp(), port), timeout);
            long rt = System.currentTimeMillis() - start;
            result.put("online", 1.0);
            result.put("response_ms", (double) rt);
        } catch (IOException e) {
            result.put("online", 0.0);
        }
        return result;
    }

    private Map<String, Double> collectHttp(Asset asset) {
        Map<String, Double> result = new HashMap<>();
        int timeout = asset.getProbeTimeoutMs() == null ? 3000 : asset.getProbeTimeoutMs();
        String url = asset.getProbeUrl();
        if (url == null || url.isBlank()) {
            url = "http://" + asset.getIp() + (asset.getProbePort() != null ? ":" + asset.getProbePort() : "");
        }
        long start = System.currentTimeMillis();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            long rt = System.currentTimeMillis() - start;
            result.put("online", (code >= 200 && code < 400) ? 1.0 : 0.0);
            result.put("response_ms", (double) rt);
            result.put("http_status", (double) code);
            conn.disconnect();
        } catch (Exception e) {
            result.put("online", 0.0);
        }
        return result;
    }

    private Map<String, Double> collectPing(Asset asset) {
        Map<String, Double> result = new HashMap<>();
        int timeout = asset.getProbeTimeoutMs() == null ? 3000 : asset.getProbeTimeoutMs();
        long start = System.currentTimeMillis();
        try {
            // Java InetAddress.isReachable 在多数容器环境无ICMP权限，优先尝试；失败则回退到TCP:80/443探测存活
            boolean reachable = java.net.InetAddress.getByName(asset.getIp()).isReachable(timeout);
            long rt = System.currentTimeMillis() - start;
            if (reachable) {
                result.put("online", 1.0);
                result.put("response_ms", (double) rt);
                return result;
            }
        } catch (Exception ignored) {
        }
        // 回退方案：尝试常见端口TCP连通性作为存活判断
        for (int port : new int[]{80, 443, 22, 3389}) {
            long s2 = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(asset.getIp(), port), Math.max(500, timeout / 4));
                result.put("online", 1.0);
                result.put("response_ms", (double) (System.currentTimeMillis() - s2));
                return result;
            } catch (IOException ignored) {
            }
        }
        result.put("online", 0.0);
        return result;
    }
}
