package com.ops.platform.service.collector;

import com.ops.platform.entity.Asset;
import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 SNMP v2c 的网络设备采集（交换机、路由器、上网行为审计网关等）。
 * 采集内容：在线状态(sysUpTime)、接口运行状态、接口流入/流出字节计数(用于CollectService计算速率)、
 * 以及（若配置了厂商私有OID）CPU/内存使用率。
 */
@Component
public class SnmpCollector {

    private static final String OID_SYS_UPTIME = "1.3.6.1.2.1.1.3.0";
    // 接口相关OID前缀，实际ifIndex从资产配置读取（默认1），支持监控非默认接口
    private static final String OID_IF_OPER_STATUS_PREFIX = "1.3.6.1.2.1.2.2.1.8.";
    private static final String OID_IF_IN_OCTETS_PREFIX = "1.3.6.1.2.1.2.2.1.10.";
    private static final String OID_IF_OUT_OCTETS_PREFIX = "1.3.6.1.2.1.2.2.1.16.";

    public Map<String, Double> collect(Asset asset) {
        Map<String, Double> result = new HashMap<>();
        Snmp snmp = null;
        try {
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();

            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(asset.getSnmpCommunity() == null ? "public" : asset.getSnmpCommunity()));
            target.setAddress(new UdpAddress(asset.getIp() + "/" + (asset.getSnmpPort() == null ? 161 : asset.getSnmpPort())));
            target.setRetries(1);
            target.setTimeout(3000);
            target.setVersion(SnmpConstants.version2c);

            Double up = getOid(snmp, target, OID_SYS_UPTIME);
            result.put("online", up == null ? 0.0 : 1.0);
            if (up == null) return result; // 不可达，直接返回

            int ifIndex = asset.getSnmpIfIndex() == null ? 1 : asset.getSnmpIfIndex();
            Double ifStatus = getOid(snmp, target, OID_IF_OPER_STATUS_PREFIX + ifIndex);
            if (ifStatus != null) result.put("if_status", ifStatus); // 1=up 2=down

            Double inOctets = getOid(snmp, target, OID_IF_IN_OCTETS_PREFIX + ifIndex);
            if (inOctets != null) result.put("if_in_octets", inOctets);

            Double outOctets = getOid(snmp, target, OID_IF_OUT_OCTETS_PREFIX + ifIndex);
            if (outOctets != null) result.put("if_out_octets", outOctets);

            if (asset.getSnmpOidCpu() != null && !asset.getSnmpOidCpu().isBlank()) {
                Double cpu = getOid(snmp, target, asset.getSnmpOidCpu());
                if (cpu != null) result.put("cpu_usage", cpu);
            }
            if (asset.getSnmpOidMem() != null && !asset.getSnmpOidMem().isBlank()) {
                Double mem = getOid(snmp, target, asset.getSnmpOidMem());
                if (mem != null) result.put("mem_usage", mem);
            }

        } catch (Exception e) {
            result.put("online", 0.0);
        } finally {
            if (snmp != null) {
                try { snmp.close(); } catch (Exception ignored) {}
            }
        }
        return result;
    }

    /** 供网络自动发现使用：尝试SNMP GET sysDescr，用于判断该IP是否为网络设备及其描述信息 */
    public String getSysDescr(String ip, String community, int port) {
        Snmp snmp = null;
        try {
            TransportMapping<?> transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();
            CommunityTarget<Address> target = new CommunityTarget<>();
            target.setCommunity(new OctetString(community == null ? "public" : community));
            target.setAddress(new UdpAddress(ip + "/" + port));
            target.setRetries(0);
            target.setTimeout(1500);
            target.setVersion(SnmpConstants.version2c);

            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID("1.3.6.1.2.1.1.1.0"))); // sysDescr
            pdu.setType(PDU.GET);
            ResponseEvent<Address> event = snmp.get(pdu, target);
            if (event == null || event.getResponse() == null) return null;
            if (event.getResponse().getErrorStatus() != PDU.noError) return null;
            return event.getResponse().get(0).getVariable().toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (snmp != null) {
                try { snmp.close(); } catch (Exception ignored) {}
            }
        }
    }

    private Double getOid(Snmp snmp, CommunityTarget<Address> target, String oid) throws Exception {
        PDU pdu = new PDU();
        pdu.add(new VariableBinding(new OID(oid)));
        pdu.setType(PDU.GET);
        ResponseEvent<Address> event = snmp.get(pdu, target);
        if (event == null || event.getResponse() == null) return null;
        PDU response = event.getResponse();
        if (response.getErrorStatus() != PDU.noError) return null;
        Variable var = response.get(0).getVariable();
        try {
            return Double.parseDouble(var.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
