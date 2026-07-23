package com.ops.platform.service.collector;

import com.jcraft.jsch.*;
import com.ops.platform.entity.Asset;
import com.ops.platform.util.AesUtil;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 基于SSH的采集与命令执行工具。
 * 覆盖场景：
 *  - Linux 物理机/虚拟机：直接执行 shell 命令
 *  - Windows 虚拟机：需开启 OpenSSH Server（Win10/Server2019+自带），通过 SSH 执行 PowerShell 命令
 *  - ESXi 宿主机：需开启 SSH 服务（DCUI/vSphere Client 中开启），执行 esxcli 等命令
 */
@Component
public class SshCollector {

    private static final int CONNECT_TIMEOUT_MS = 6000;
    private static final int EXEC_TIMEOUT_MS = 15000;

    /** 采集 Linux 系统的 CPU/内存/磁盘/负载 */
    public Map<String, Double> collectLinux(Asset asset) {
        Map<String, Double> result = new HashMap<>();
        Session session = null;
        try {
            session = openSession(asset);

            String cpuIdle = exec(session,
                "top -bn1 | grep -i 'Cpu(s)' | awk -F',' '{for(i=1;i<=NF;i++){if($i ~ /id/){gsub(/[^0-9.]/,\"\",$i);print $i}}}'");
            if (!cpuIdle.isBlank()) {
                double idle = parseDouble(cpuIdle);
                result.put("cpu_usage", round2(100 - idle));
            }

            String mem = exec(session, "free | grep Mem | awk '{printf \"%.2f\", $3/$2*100}'");
            if (!mem.isBlank()) result.put("mem_usage", parseDouble(mem));

            String disk = exec(session, "df -h / | awk 'NR==2{gsub(/%/,\"\",$5); print $5}'");
            if (!disk.isBlank()) result.put("disk_usage", parseDouble(disk));

            String load = exec(session, "cat /proc/loadavg | awk '{print $1}'");
            if (!load.isBlank()) result.put("load1", parseDouble(load));

        } catch (Exception e) {
            result.put("_error", 1.0);
        } finally {
            closeSession(session);
        }
        return result;
    }

    /** 采集 Windows（需OpenSSH+PowerShell）的 CPU/内存/磁盘 */
    public Map<String, Double> collectWindows(Asset asset) {
        Map<String, Double> result = new HashMap<>();
        Session session = null;
        try {
            session = openSession(asset);

            String cpu = exec(session,
                "powershell -NoProfile -Command \"(Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average\"");
            if (!cpu.isBlank()) result.put("cpu_usage", parseDouble(cpu));

            String mem = exec(session,
                "powershell -NoProfile -Command \"$os=Get-CimInstance Win32_OperatingSystem; [math]::Round((($os.TotalVisibleMemorySize-$os.FreePhysicalMemory)/$os.TotalVisibleMemorySize)*100,2)\"");
            if (!mem.isBlank()) result.put("mem_usage", parseDouble(mem));

            String disk = exec(session,
                "powershell -NoProfile -Command \"$d=Get-CimInstance Win32_LogicalDisk -Filter \\\"DeviceID='C:'\\\"; [math]::Round((($d.Size-$d.FreeSpace)/$d.Size)*100,2)\"");
            if (!disk.isBlank()) result.put("disk_usage", parseDouble(disk));

        } catch (Exception e) {
            result.put("_error", 1.0);
        } finally {
            closeSession(session);
        }
        return result;
    }

    /** ESXi 宿主机：MVP阶段仅做连通性/在线状态检测，深度性能指标建议后续接入 vSphere API */
    public Map<String, Double> collectEsxi(Asset asset) {
        Map<String, Double> result = new HashMap<>();
        Session session = null;
        try {
            session = openSession(asset);
            String uptime = exec(session, "esxcli system uptime get");
            result.put("online", uptime.isBlank() ? 0.0 : 1.0);
        } catch (Exception e) {
            result.put("online", 0.0);
        } finally {
            closeSession(session);
        }
        return result;
    }

    /** 执行自动化运维任务脚本，返回输出内容，供任务执行服务调用 */
    public ExecResult runScript(Asset asset, String scriptType, String scriptContent) {
        Session session = null;
        try {
            session = openSession(asset);
            String cmd = scriptContent;
            if ("powershell".equalsIgnoreCase(scriptType)) {
                String escaped = scriptContent.replace("\"", "\\\"");
                cmd = "powershell -NoProfile -Command \"" + escaped + "\"";
            }
            ExecResult r = execWithExitCode(session, cmd, 120000);
            return r;
        } catch (Exception e) {
            ExecResult r = new ExecResult();
            r.exitCode = -1;
            r.output = "连接或执行异常: " + e.getMessage();
            return r;
        } finally {
            closeSession(session);
        }
    }

    // ---------------- 内部辅助方法 ----------------

    private Session openSession(Asset asset) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(asset.getSshUser(), asset.getIp(), asset.getSshPort() == null ? 22 : asset.getSshPort());
        if (Boolean.TRUE.equals(asset.getSshUseKey()) && asset.getSshPrivateKeyEnc() != null && !asset.getSshPrivateKeyEnc().isBlank()) {
            byte[] key = AesUtil.decrypt(asset.getSshPrivateKeyEnc()).getBytes();
            jsch.addIdentity("key-" + asset.getId(), key, null, null);
        } else {
            session.setPassword(AesUtil.decrypt(asset.getSshPasswordEnc()));
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.setTimeout(CONNECT_TIMEOUT_MS);
        session.connect(CONNECT_TIMEOUT_MS);
        return session;
    }

    private void closeSession(Session session) {
        if (session != null && session.isConnected()) session.disconnect();
    }

    private String exec(Session session, String command) throws Exception {
        return execWithExitCode(session, command, EXEC_TIMEOUT_MS).output.trim();
    }

    private ExecResult execWithExitCode(Session session, String command, int timeoutMs) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setErrStream(System.err);
        InputStream in = channel.getInputStream();
        channel.connect(CONNECT_TIMEOUT_MS);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] tmp = new byte[1024];
        long start = System.currentTimeMillis();
        while (true) {
            while (in.available() > 0) {
                int i = in.read(tmp, 0, 1024);
                if (i < 0) break;
                buffer.write(tmp, 0, i);
            }
            if (channel.isClosed()) {
                if (in.available() > 0) continue;
                break;
            }
            if (System.currentTimeMillis() - start > timeoutMs) {
                channel.disconnect();
                throw new RuntimeException("命令执行超时");
            }
            Thread.sleep(200);
        }
        ExecResult result = new ExecResult();
        result.exitCode = channel.getExitStatus();
        result.output = buffer.toString("UTF-8");
        channel.disconnect();
        return result;
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    public static class ExecResult {
        public int exitCode;
        public String output;
    }
}
