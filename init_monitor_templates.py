#!/usr/bin/env python3
"""
初始化IT监控模板 - 覆盖网络设备、服务器、数据库、中间件等
写入 exec_templates 表，供监控中心"从模板添加"使用
"""
import os
import sys
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'spug.settings')
os.environ.setdefault('MYSQL_DATABASE', 'spug')
os.environ.setdefault('MYSQL_USER', 'root')
os.environ.setdefault('MYSQL_PASSWORD', 'Clbr@Mysql2024')
os.environ.setdefault('MYSQL_HOST', '127.0.0.1')
os.environ.setdefault('MYSQL_PORT', '3306')
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
django.setup()

from apps.exec.models import ExecTemplate
from apps.account.models import User

ADMIN = User.objects.filter(is_supper=True).first()

TEMPLATES = []

# ============================================================
# 一、网络设备监控
# ============================================================

TEMPLATES.append({
    'name': '二层交换机-SNMP状态巡检',
    'type': '网络设备',
    'interpreter': 'sh',
    'desc': '通过SNMP获取交换机基础状态：系统信息、运行时间、CPU/内存利用率、接口状态统计',
    'body': r'''#!/bin/bash
# 二层交换机SNMP状态巡检
# 需要在目标主机安装net-snmp: yum/apt install net-snmp-utils
SNMP_VER="${SNMP_VER:-2c}"
SNMP_COMM="${SNMP_COMM:-public}"
SNMP_HOST="${SNMP_HOST:-127.0.0.1}"
SNMP_PORT="${SNMP_PORT:-161}"

check_snmp() {
    snmpget -v "$SNMP_VER" -c "$SNMP_COMM" "$SNMP_HOST":"$SNMP_PORT" "$1" 2>/dev/null | awk -F': ' '{print $2}' | tr -d '"'
}

SYS_DESC=$(check_snmp 1.3.6.1.2.1.1.1.0)
SYS_UPTIME=$(check_snmp 1.3.6.1.2.1.1.3.0)
SYS_NAME=$(check_snmp 1.3.6.1.2.1.1.5.0)

if [ -z "$SYS_DESC" ]; then
    echo "CRITICAL: SNMP连接失败，请检查SNMP配置和社区字符串"
    exit 1
fi

echo "系统描述: $SYS_DESC"
echo "系统名称: $SYS_NAME"
echo "运行时间: $SYS_UPTIME"

# CPU利用率 (H3C/华为私有OID, 通用设备跳过)
CPU_OID="1.3.6.1.4.1.25506.2.6.1.1.1.1.6"
CPU_VAL=$(check_snmp "$CPU_OID" 2>/dev/null)
if [ -n "$CPU_VAL" ] && [ "$CPU_VAL" != "No Such Object" ]; then
    echo "CPU利用率: ${CPU_VAL}%"
    if [ "$CPU_VAL" -gt 90 ] 2>/dev/null; then
        echo "WARNING: CPU利用率超过90%"
        exit 1
    fi
fi

# 内存利用率 (H3C/华为私有OID)
MEM_OID="1.3.6.1.4.1.25506.2.6.1.1.1.1.8"
MEM_VAL=$(check_snmp "$MEM_OID" 2>/dev/null)
if [ -n "$MEM_VAL" ] && [ "$MEM_VAL" != "No Such Object" ]; then
    echo "内存利用率: ${MEM_VAL}%"
    if [ "$MEM_VAL" -gt 90 ] 2>/dev/null; then
        echo "WARNING: 内存利用率超过90%"
        exit 1
    fi
fi

# 接口状态统计
UP_COUNT=0; DOWN_COUNT=0; TOTAL=0
for status in $(snmpwalk -v "$SNMP_VER" -c "$SNMP_COMM" "$SNMP_HOST":"$SNMP_PORT" 1.3.6.1.2.1.2.2.1.8 2>/dev/null | awk -F': ' '{print $2}'); do
    TOTAL=$((TOTAL + 1))
    if [ "$status" = "1" ]; then
        UP_COUNT=$((UP_COUNT + 1))
    else
        DOWN_COUNT=$((DOWN_COUNT + 1))
    fi
done
echo "接口统计: 总计$TOTAL, UP=$UP_COUNT, DOWN=$DOWN_COUNT"

echo "OK: 交换机状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': '二层交换机-接口流量监控',
    'type': '网络设备',
    'interpreter': 'sh',
    'desc': '通过SNMP获取交换机各接口入/出流量(bps)，检测流量异常',
    'body': r'''#!/bin/bash
# 二层交换机接口流量监控
SNMP_VER="${SNMP_VER:-2c}"
SNMP_COMM="${SNMP_COMM:-public}"
SNMP_HOST="${SNMP_HOST:-127.0.0.1}"
SNMP_PORT="${SNMP_PORT:-161}"
FLOW_THRESHOLD="${FLOW_THRESHOLD:-900000000}"

get_if_names() {
    snmpwalk -v "$SNMP_VER" -c "$SNMP_COMM" "$SNMP_HOST":"$SNMP_PORT" 1.3.6.1.2.1.2.2.1.2 2>/dev/null
}

get_if_speed() {
    snmpget -v "$SNMP_VER" -c "$SNMP_COMM" "$SNMP_HOST":"$SNMP_PORT" "1.3.6.1.2.1.2.2.1.5.$1" 2>/dev/null | awk -F': ' '{print $2}'
}

get_if_octets() {
    snmpget -v "$SNMP_VER" -c "$SNMP_COMM" "$SNMP_HOST":"$SNMP_PORT" "$1.$2" 2>/dev/null | awk -F': ' '{print $2}'
}

HAS_ERROR=0
while IFS= read -r line; do
    IF_IDX=$(echo "$line" | awk -F'.' '{print $NF}' | awk '{print $1}')
    IF_NAME=$(echo "$line" | awk -F': ' '{print $2}' | tr -d '"')
    IF_STATUS=$(snmpget -v "$SNMP_VER" -c "$SNMP_COMM" "$SNMP_HOST":"$SNMP_PORT" "1.3.6.1.2.1.2.2.1.8.$IF_IDX" 2>/dev/null | awk -F': ' '{print $2}')
    [ "$IF_STATUS" != "1" ] && continue

    OCTETS_IN_1=$(get_if_octets "1.3.6.1.2.1.2.2.1.10" "$IF_IDX")
    OCTETS_OUT_1=$(get_if_octets "1.3.6.1.2.1.2.2.1.16" "$IF_IDX")
    sleep 5
    OCTETS_IN_2=$(get_if_octets "1.3.6.1.2.1.2.2.1.10" "$IF_IDX")
    OCTETS_OUT_2=$(get_if_octets "1.3.6.1.2.1.2.2.1.16" "$IF_IDX")

    if [ -n "$OCTETS_IN_1" ] && [ -n "$OCTETS_IN_2" ]; then
        BPS_IN=$(( (OCTETS_IN_2 - OCTETS_IN_1) * 8 / 5 ))
        BPS_OUT=$(( (OCTETS_OUT_2 - OCTETS_OUT_1) * 8 / 5 ))
        echo "$IF_NAME: 入流量=${BPS_IN}bps, 出流量=${BPS_OUT}bps"
        if [ "$BPS_IN" -gt "$FLOW_THRESHOLD" ] 2>/dev/null; then
            echo "WARNING: $IF_NAME 入流量超过阈值"
            HAS_ERROR=1
        fi
    fi
done < <(get_if_names)

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 接口流量正常"
exit 0
''',
})

TEMPLATES.append({
    'name': '上网行为审计-服务状态检测',
    'type': '网络设备',
    'interpreter': 'sh',
    'desc': '检测上网行为审计设备的Web管理端口和SNMP可达性',
    'body': r'''#!/bin/bash
# 上网行为审计设备服务状态检测
AUDIT_HOST="${AUDIT_HOST:-127.0.0.1}"
AUDIT_WEB_PORT="${AUDIT_WEB_PORT:-443}"
AUDIT_SNMP_PORT="${AUDIT_SNMP_PORT:-161}"

# Web管理端口检测
timeout 5 bash -c "echo >/dev/tcp/$AUDIT_HOST/$AUDIT_WEB_PORT" 2>/dev/null
if [ $? -ne 0 ]; then
    echo "CRITICAL: Web管理端口 $AUDIT_WEB_PORT 不可达"
    exit 1
fi
echo "Web管理端口 $AUDIT_WEB_PORT: 正常"

# SNMP可达性检测
if command -v snmpget &>/dev/null; then
    RESULT=$(snmpget -v 2c -c public "$AUDIT_HOST":"$AUDIT_SNMP_PORT" 1.3.6.1.2.1.1.1.0 2>/dev/null)
    if [ -z "$RESULT" ]; then
        echo "WARNING: SNMP不可达"
        exit 1
    fi
    echo "SNMP: 正常"
fi

# Ping检测
ping -c 1 -W 3 "$AUDIT_HOST" &>/dev/null
if [ $? -ne 0 ]; then
    echo "CRITICAL: Ping不可达"
    exit 1
fi
echo "Ping: 正常"

echo "OK: 上网行为审计设备状态正常"
exit 0
''',
})

# ============================================================
# 二、VMware ESXi 监控
# ============================================================

TEMPLATES.append({
    'name': 'ESXi主机-SSH状态巡检',
    'type': 'VMware',
    'interpreter': 'sh',
    'desc': '通过SSH连接ESXi Shell执行esxcli命令检查主机状态：CPU/内存/存储/网络/VM运行状态',
    'body': r'''#!/bin/bash
# ESXi主机SSH状态巡检
# 注意: 需在Spug中添加ESXi主机并启用SSH

ESXI_HOST="${ESXI_HOST:-localhost}"
HAS_ERROR=0

# 主机基本信息
echo "=== 主机信息 ==="
esxcli system version get 2>/dev/null || echo "WARNING: esxcli命令不可用"
esxcli system hostname get 2>/dev/null

# CPU使用率
echo "=== CPU使用率 ==="
CPU_INFO=$(esxcli hardware cpu global get 2>/dev/null)
echo "$CPU_INFO"

# 内存使用率
echo "=== 内存使用率 ==="
MEM_INFO=$(esxcli hardware memory get 2>/dev/null)
echo "$MEM_INFO"
MEM_USED_KB=$(grep "Used" <<< "$MEM_INFO" 2>/dev/null | awk '{print $3}')
MEM_TOTAL_KB=$(grep "Physical Memory" <<< "$MEM_INFO" 2>/dev/null | awk '{print $3}')
if [ -n "$MEM_USED_KB" ] && [ -n "$MEM_TOTAL_KB" ] && [ "$MEM_TOTAL_KB" -gt 0 ] 2>/dev/null; then
    MEM_PCT=$((MEM_USED_KB * 100 / MEM_TOTAL_KB))
    echo "内存使用率: ${MEM_PCT}%"
    [ "$MEM_PCT" -gt 95 ] && echo "WARNING: 内存使用率超过95%" && HAS_ERROR=1
fi

# 存储状态
echo "=== 存储状态 ==="
esxcli storage filesystem list 2>/dev/null | while read -r line; do
    echo "$line"
    PCT=$(echo "$line" | awk '{print $4}' | tr -d '%' 2>/dev/null)
    if [ -n "$PCT" ] && [ "$PCT" -gt 95 ] 2>/dev/null; then
        echo "WARNING: 存储使用率超过95%"
    fi
done

# 网络连通性
echo "=== 网络接口 ==="
esxcli network nic list 2>/dev/null | head -20

# VM运行状态
echo "=== 虚拟机状态 ==="
VM_LIST=$(vim-cmd vmsvc/getallvms 2>/dev/null)
if [ -n "$VM_LIST" ]; then
    echo "$VM_LIST"
    while read -r vmid vmname _; do
        [ -z "$vmid" ] && continue
        [[ ! "$vmid" =~ ^[0-9]+$ ]] && continue
        VM_STATE=$(vim-cmd vmsvc/power.getstate "$vmid" 2>/dev/null | grep "Powered")
        echo "VM $vmname ($vmid): $VM_STATE"
    done <<< "$VM_LIST"
fi

# 告警检查
echo "=== 硬件告警 ==="
ALERTS=$(esxcli hardware ipmi sel list 2>/dev/null | grep -c "Critical\|Warning" || true)
if [ "$ALERTS" -gt 0 ] 2>/dev/null; then
    echo "WARNING: 检测到 $ALERTS 条硬件告警"
    HAS_ERROR=1
else
    echo "无硬件告警"
fi

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: ESXi主机状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'ESXi主机-数据存储空间监控',
    'type': 'VMware',
    'interpreter': 'sh',
    'desc': '检查ESXi各数据存储的可用空间，超过阈值告警',
    'body': r'''#!/bin/bash
# ESXi数据存储空间监控
THRESHOLD_PCT="${THRESHOLD_PCT:-90}"

HAS_ERROR=0
esxcli storage filesystem list 2>/dev/null | tail -n +3 | while read -r line; do
    DS_NAME=$(echo "$line" | awk '{print $1}')
    DS_FREE=$(echo "$line" | awk '{print $5}')
    DS_TOTAL=$(echo "$line" | awk '{print $4}')
    if [ -n "$DS_TOTAL" ] && [ "$DS_TOTAL" -gt 0 ] 2>/dev/null; then
        DS_USED_PCT=$(( (DS_TOTAL - DS_FREE) * 100 / DS_TOTAL ))
        echo "$DS_NAME: 已用${DS_USED_PCT}% (可用${DS_FREE}KB)"
        if [ "$DS_USED_PCT" -gt "$THRESHOLD_PCT" ] 2>/dev/null; then
            echo "WARNING: $DS_NAME 使用率超过 ${THRESHOLD_PCT}%"
            HAS_ERROR=1
        fi
    fi
done

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 数据存储空间正常"
exit 0
''',
})

# ============================================================
# 三、Linux服务器监控
# ============================================================

TEMPLATES.append({
    'name': 'Linux-CentOS/Ubuntu/KylinOS-系统综合巡检',
    'type': 'Linux服务器',
    'interpreter': 'sh',
    'desc': 'Linux系统综合巡检：CPU/内存/磁盘/负载/网络/系统信息，兼容CentOS/Ubuntu/KylinOS',
    'body': r'''#!/bin/bash
# Linux系统综合巡检 (兼容CentOS/Ubuntu/KylinOS)
THRESHOLD_CPU="${THRESHOLD_CPU:-90}"
THRESHOLD_MEM="${THRESHOLD_MEM:-90}"
THRESHOLD_DISK="${THRESHOLD_DISK:-90}"
THRESHOLD_LOAD="${THRESHOLD_LOAD:-8}"
HAS_ERROR=0

# 系统信息
echo "=== 系统信息 ==="
echo "主机名: $(hostname)"
echo "系统: $(cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d'"' -f2 || uname -s -r)"
echo "内核: $(uname -r)"
echo "运行时间: $(uptime -p 2>/dev/null || uptime)"
echo "架构: $(uname -m)"

# CPU使用率
echo "=== CPU ==="
CPU_IDLE=$(top -bn1 | grep "Cpu(s)" | awk '{print $8}' | cut -d'.' -f1)
if [ -z "$CPU_IDLE" ]; then
    CPU_IDLE=$(vmstat 1 2 | tail -1 | awk '{print $15}')
fi
CPU_USED=$((100 - CPU_IDLE))
echo "CPU使用率: ${CPU_USED}%"
[ "$CPU_USED" -gt "$THRESHOLD_CPU" ] 2>/dev/null && echo "WARNING: CPU使用率超过${THRESHOLD_CPU}%" && HAS_ERROR=1

# CPU核心数
CPU_CORES=$(nproc 2>/dev/null || grep -c ^processor /proc/cpuinfo)
echo "CPU核心: ${CPU_CORES}"

# 内存使用率
echo "=== 内存 ==="
if command -v free &>/dev/null; then
    MEM_TOTAL=$(free -m | awk '/^Mem:/{print $2}')
    MEM_USED=$(free -m | awk '/^Mem:/{print $3}')
    MEM_AVAIL=$(free -m | awk '/^Mem:/{print $7}')
    if [ -n "$MEM_AVAIL" ]; then
        MEM_USED_ACTUAL=$((MEM_TOTAL - MEM_AVAIL))
        MEM_PCT=$((MEM_USED_ACTUAL * 100 / MEM_TOTAL))
    else
        MEM_PCT=$((MEM_USED * 100 / MEM_TOTAL))
    fi
    echo "内存: 已用${MEM_USED}MB/总计${MEM_TOTAL}MB (${MEM_PCT}%)"
else
    MEM_INFO=$(cat /proc/meminfo)
    MEM_TOTAL=$(echo "$MEM_INFO" | grep MemTotal | awk '{print $2}')
    MEM_AVAIL=$(echo "$MEM_INFO" | grep MemAvailable | awk '{print $2}')
    MEM_PCT=$(( (MEM_TOTAL - MEM_AVAIL) * 100 / MEM_TOTAL ))
    echo "内存使用率: ${MEM_PCT}%"
fi
[ "$MEM_PCT" -gt "$THRESHOLD_MEM" ] 2>/dev/null && echo "WARNING: 内存使用率超过${THRESHOLD_MEM}%" && HAS_ERROR=1

# Swap使用
SWAP_TOTAL=$(free -m | awk '/^Swap:/{print $2}')
SWAP_USED=$(free -m | awk '/^Swap:/{print $3}')
if [ "$SWAP_TOTAL" -gt 0 ] 2>/dev/null; then
    SWAP_PCT=$((SWAP_USED * 100 / SWAP_TOTAL))
    echo "Swap: 已用${SWAP_USED}MB/总计${SWAP_TOTAL}MB (${SWAP_PCT}%)"
fi

# 磁盘使用率
echo "=== 磁盘 ==="
df -h -x tmpfs -x devtmpfs -x squashfs -x overlay 2>/dev/null | while read -r line; do
    PCT=$(echo "$line" | awk '{print $5}' | tr -d '%')
    MOUNT=$(echo "$line" | awk '{print $6}')
    if [ "$PCT" -gt "$THRESHOLD_DISK" ] 2>/dev/null; then
        echo "WARNING: $MOUNT 使用率 ${PCT}% 超过${THRESHOLD_DISK}%"
    fi
done
# 重新检查是否有超限磁盘
DISK_WARN=$(df -h -x tmpfs -x devtmpfs -x squashfs -x overlay 2>/dev/null | awk '{print $5}' | tr -d '%' | grep -E '^[0-9]+$' | awk -v t="$THRESHOLD_DISK" '$1 > t')
[ -n "$DISK_WARN" ] && HAS_ERROR=1

# 系统负载
echo "=== 负载 ==="
LOAD1=$(cat /proc/loadavg | awk '{print $1}')
LOAD5=$(cat /proc/loadavg | awk '{print $2}')
LOAD15=$(cat /proc/loadavg | awk '{print $3}')
echo "负载: 1min=$LOAD1, 5min=$LOAD5, 15min=$LOAD15"
LOAD_INT=$(echo "$LOAD1" | cut -d'.' -f1)
[ "$LOAD_INT" -gt "$THRESHOLD_LOAD" ] 2>/dev/null && echo "WARNING: 系统负载过高" && HAS_ERROR=1

# 网络连接状态
echo "=== 网络 ==="
ESTAB=$(ss -ant 2>/dev/null | grep -c ESTAB || netstat -ant 2>/dev/null | grep -c ESTABLISHED)
TIME_WAIT=$(ss -ant 2>/dev/null | grep -c TIME-WAIT || netstat -ant 2>/dev/null | grep -c TIME_WAIT)
echo "TCP连接: ESTABLISHED=$ESTAB, TIME_WAIT=$TIME_WAIT"

# IO等待
IOWAIT=$(iostat -c 1 2 2>/dev/null | tail -1 | awk '{print $4}')
[ -n "$IOWAIT" ] && echo "IO Wait: ${IOWAIT}%"

# Zombie进程
ZOMBIES=$(ps aux | awk '{if($8=="Z") print}' | wc -l)
echo "僵尸进程: $ZOMBIES"
[ "$ZOMBIES" -gt 10 ] 2>/dev/null && echo "WARNING: 僵尸进程过多" && HAS_ERROR=1

# 最近登录失败
LOGIN_FAIL=$(lastb 2>/dev/null | wc -l)
echo "最近登录失败: ${LOGIN_FAIL}次"
[ "$LOGIN_FAIL" -gt 100 ] 2>/dev/null && echo "WARNING: 登录失败次数过多，可能存在暴力破解" && HAS_ERROR=1

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 系统状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Linux-磁盘IO性能监控',
    'type': 'Linux服务器',
    'interpreter': 'sh',
    'desc': '监控磁盘IO使用率、读写延迟、队列深度，检测IO瓶颈',
    'body': r'''#!/bin/bash
# Linux磁盘IO性能监控
THRESHOLD_UTIL="${THRESHOLD_UTIL:-90}"
THRESHOLD_AWAIT="${THRESHOLD_AWAIT:-50}"

HAS_ERROR=0
if ! command -v iostat &>/dev/null; then
    echo "UNKNOWN: iostat未安装，请安装sysstat包"
    exit 1
fi

echo "=== 磁盘IO统计 ==="
iostat -dx 1 2 2>/dev/null | tail -n +4 | while read -r line; do
    DEVICE=$(echo "$line" | awk '{print $1}')
    UTIL=$(echo "$line" | awk '{print $NF}')
    AWAIT=$(echo "$line" | awk '{print $(NF-2)}')
    [ -z "$UTIL" ] && continue
    echo "$DEVICE: util=${UTIL}%, await=${AWAIT}ms"
    UTIL_INT=$(echo "$UTIL" | cut -d'.' -f1)
    AWAIT_INT=$(echo "$AWAIT" | cut -d'.' -f1)
    if [ "$UTIL_INT" -gt "$THRESHOLD_UTIL" ] 2>/dev/null; then
        echo "WARNING: $DEVICE IO利用率超过${THRESHOLD_UTIL}%"
        HAS_ERROR=1
    fi
    if [ "$AWAIT_INT" -gt "$THRESHOLD_AWAIT" ] 2>/dev/null; then
        echo "WARNING: $DEVICE IO延迟超过${THRESHOLD_AWAIT}ms"
        HAS_ERROR=1
    fi
done

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 磁盘IO正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Linux-网络流量与连接监控',
    'type': 'Linux服务器',
    'interpreter': 'sh',
    'desc': '监控网络接口流量、TCP连接数、连接状态分布',
    'body': r'''#!/bin/bash
# Linux网络流量与连接监控
THRESHOLD_CONN="${THRESHOLD_CONN:-50000}"
HAS_ERROR=0

echo "=== 网络接口流量 ==="
for iface in $(ls /sys/class/net/ 2>/dev/null | grep -v lo); do
    RX_BYTES=$(cat /sys/class/net/$iface/statistics/rx_bytes 2>/dev/null)
    TX_BYTES=$(cat /sys/class/net/$iface/statistics/tx_bytes 2>/dev/null)
    RX_MB=$((RX_BYTES / 1048576))
    TX_MB=$((TX_BYTES / 1048576))
    echo "$iface: RX=${RX_MB}MB, TX=${TX_MB}MB"
done

echo "=== TCP连接统计 ==="
if command -v ss &>/dev/null; then
    ESTAB=$(ss -ant | grep -c ESTAB)
    TIME_WAIT=$(ss -ant | grep -c TIME-WAIT)
    CLOSE_WAIT=$(ss -ant | grep -c CLOSE-WAIT)
    TOTAL=$(ss -ant | tail -n +2 | wc -l)
else
    ESTAB=$(netstat -ant | grep -c ESTABLISHED)
    TIME_WAIT=$(netstat -ant | grep -c TIME_WAIT)
    CLOSE_WAIT=$(netstat -ant | grep -c CLOSE_WAIT)
    TOTAL=$(netstat -ant | tail -n +3 | wc -l)
fi
echo "总连接: $TOTAL, ESTABLISHED: $ESTAB, TIME_WAIT: $TIME_WAIT, CLOSE_WAIT: $CLOSE_WAIT"

[ "$TOTAL" -gt "$THRESHOLD_CONN" ] 2>/dev/null && echo "WARNING: TCP连接数超过${THRESHOLD_CONN}" && HAS_ERROR=1
[ "$CLOSE_WAIT" -gt 1000 ] 2>/dev/null && echo "WARNING: CLOSE_WAIT过多，可能存在连接泄漏" && HAS_ERROR=1

echo "=== 监听端口 ==="
ss -tlnp 2>/dev/null | head -20 || netstat -tlnp 2>/dev/null | head -20

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 网络状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Linux-系统安全巡检',
    'type': 'Linux服务器',
    'interpreter': 'sh',
    'desc': '检查SSH配置、防火墙状态、SELinux、异常进程、SUID文件等安全项',
    'body': r'''#!/bin/bash
# Linux系统安全巡检
HAS_ERROR=0

echo "=== SSH配置检查 ==="
SSH_CONFIG="/etc/ssh/sshd_config"
if [ -f "$SSH_CONFIG" ]; then
    PERMIT_ROOT=$(grep -E "^PermitRootLogin" "$SSH_CONFIG" 2>/dev/null | awk '{print $2}')
    PASS_AUTH=$(grep -E "^PasswordAuthentication" "$SSH_CONFIG" 2>/dev/null | awk '{print $2}')
    PORT=$(grep -E "^Port" "$SSH_CONFIG" 2>/dev/null | awk '{print $2}')
    echo "Root登录: ${PERMIT_ROOT:-yes}"
    echo "密码认证: ${PASS_AUTH:-yes}"
    echo "SSH端口: ${PORT:-22}"
    [ "$PERMIT_ROOT" = "yes" ] && echo "NOTICE: 允许Root直接登录"
fi

echo "=== 防火墙状态 ==="
if command -v firewall-cmd &>/dev/null; then
    FW_STATE=$(firewall-cmd --state 2>/dev/null)
    echo "Firewalld: $FW_STATE"
elif command -v ufw &>/dev/null; then
    FW_STATE=$(ufw status 2>/dev/null | head -1)
    echo "UFW: $FW_STATE"
elif command -v iptables &>/dev/null; then
    IPT_RULES=$(iptables -L 2>/dev/null | wc -l)
    echo "iptables规则数: $IPT_RULES"
fi

echo "=== SELinux/AppArmor ==="
if command -v getenforce &>/dev/null; then
    echo "SELinux: $(getenforce 2>/dev/null)"
elif command -v aa-status &>/dev/null; then
    echo "AppArmor: $(aa-status 2>/dev/null | head -1)"
fi

echo "=== 异常进程检查 ==="
ZOMBIES=$(ps aux | awk '{if($8=="Z") print}' | wc -l)
echo "僵尸进程: $ZOMBIES"
[ "$ZOMBIES" -gt 5 ] 2>/dev/null && echo "WARNING: 僵尸进程过多" && HAS_ERROR=1

echo "=== SUID文件检查 ==="
SUID_COUNT=$(find /usr/bin /usr/sbin /bin /sbin -perm -4000 -type f 2>/dev/null | wc -l)
echo "SUID文件数: $SUID_COUNT"

echo "=== 最近登录失败 ==="
lastb 2>/dev/null | head -5
LOGIN_FAIL=$(lastb 2>/dev/null | wc -l)
[ "$LOGIN_FAIL" -gt 50 ] 2>/dev/null && echo "WARNING: 登录失败${LOGIN_FAIL}次，可能存在暴力破解" && HAS_ERROR=1

echo "=== 可疑定时任务 ==="
CRON_FILES=$(find /etc/cron.* /var/spool/cron -type f 2>/dev/null | wc -l)
echo "定时任务文件数: $CRON_FILES"

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 安全巡检完成"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Linux-KylinOS-国产化适配巡检',
    'type': 'Linux服务器',
    'interpreter': 'sh',
    'desc': '针对麒麟操作系统的国产化适配巡检：CPU架构、国产软件、许可证、安全加固状态',
    'body': r'''#!/bin/bash
# KylinOS国产化适配巡检
HAS_ERROR=0

echo "=== 系统信息 ==="
if [ -f /etc/os-release ]; then
    . /etc/os-release
    echo "系统: $PRETTY_NAME"
    echo "ID: $ID"
    echo "版本: $VERSION"
fi

echo "=== CPU架构 ==="
ARCH=$(uname -m)
echo "架构: $ARCH"
case "$ARCH" in
    aarch64) echo "ARM64架构(国产CPU适配)" ;;
    loongarch64) echo "龙芯架构" ;;
    sw_64) echo "申威架构" ;;
    mips64) echo "MIPS架构(龙芯)" ;;
    x86_64) echo "x86_64架构" ;;
    *) echo "其他架构: $ARCH" ;;
esac

echo "=== 国产软件检查 ==="
for cmd in nginx mysql psql redis-server java python3; do
    if command -v "$cmd" &>/dev/null; then
        VER=$("$cmd" --version 2>&1 | head -1)
        echo "$cmd: $VER"
    else
        echo "$cmd: 未安装"
    fi
done

echo "=== 许可证状态 ==="
if [ -f /etc/.kylin-act ]; then
    echo "激活状态: 已激活"
elif [ -f /etc/.kylin-license ]; then
    echo "许可证: 存在"
else
    echo "NOTICE: 未检测到激活信息"
fi

echo "=== 安全加固 ==="
if [ -f /etc/security/limits.conf ]; then
    NOFILE=$(grep -E "^\*[[:space:]]+hard[[:space:]]+nofile" /etc/security/limits.conf 2>/dev/null | awk '{print $4}')
    echo "文件描述符限制: ${NOFILE:-默认}"
fi

echo "=== 内核模块 ==="
MODULES=$(lsmod | wc -l)
echo "已加载内核模块: $MODULES"

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: KylinOS巡检完成"
exit 0
''',
})

# ============================================================
# 四、Windows服务器监控
# ============================================================

TEMPLATES.append({
    'name': 'Windows-系统综合巡检',
    'type': 'Windows服务器',
    'interpreter': 'sh',
    'desc': '通过SSH(WinRM/OpenSSH)连接Windows执行PowerShell命令检查系统状态',
    'body': r'''#!/bin/bash
# Windows系统综合巡检 (通过SSH执行PowerShell)
# 前提: Windows已安装OpenSSH Server并允许SSH连接
THRESHOLD_CPU="${THRESHOLD_CPU:-90}"
THRESHOLD_MEM="${THRESHOLD_MEM:-90}"
THRESHOLD_DISK="${THRESHOLD_DISK:-90}"

PS_CMD="powershell -NoProfile -Command"
HAS_ERROR=0

echo "=== 系统信息 ==="
$PS_CMD "Get-ComputerInfo | Select-Object CsName, WindowsVersion, OsArchitecture, OsUptime | Format-List" 2>/dev/null

echo "=== CPU使用率 ==="
CPU=$($PS_CMD "(Get-Counter '\\Processor(_Total)\\% Processor Time' -SampleInterval 2 -MaxSamples 1).CounterSamples.CookedValue" 2>/dev/null | awk '{print int($1)}')
echo "CPU使用率: ${CPU}%"
[ -n "$CPU" ] && [ "$CPU" -gt "$THRESHOLD_CPU" ] 2>/dev/null && echo "WARNING: CPU使用率超过${THRESHOLD_CPU}%" && HAS_ERROR=1

echo "=== 内存使用率 ==="
MEM_DATA=$($PS_CMD "$os=Get-CimInstance Win32_OperatingSystem; [math]::Round(($os.TotalVisibleMemorySize-$os.FreePhysicalMemory)/$os.TotalVisibleMemorySize*100,1)" 2>/dev/null)
echo "内存使用率: ${MEM_DATA}%"
[ -n "$MEM_DATA" ] && [ "$(echo "$MEM_DATA" | cut -d'.' -f1)" -gt "$THRESHOLD_MEM" ] 2>/dev/null && echo "WARNING: 内存使用率超过${THRESHOLD_MEM}%" && HAS_ERROR=1

echo "=== 磁盘使用率 ==="
$PS_CMD "Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | ForEach-Object { \$pct=[math]::Round(\$_.UsedSpace/\$_.Size*100,1) -replace 'NaN','0'; Write-Output \"\$(\$_.DeviceID) 已用\${pct}% 可用\$([math]::Round(\$_.FreeSpace/1GB,1))GB\" }" 2>/dev/null

echo "=== 关键服务状态 ==="
for svc in "EventLog" "DNS Client" "DHCP Client" "Windows Firewall" "Remote Desktop Services"; do
    SVC_STATE=$($PS_CMD "(Get-Service -DisplayName '$svc' -ErrorAction SilentlyContinue).Status" 2>/dev/null)
    echo "$svc: ${SVC_STATE:-未找到}"
done

echo "=== 系统事件错误 ==="
ERR_COUNT=$($PS_CMD "(Get-EventLog -LogName System -EntryType Error -After (Get-Date).AddHours(-1) -ErrorAction SilentlyContinue).Count" 2>/dev/null)
echo "最近1小时系统错误事件: ${ERR_COUNT:-0}"

echo "=== 进程Top5 ==="
$PS_CMD "Get-Process | Sort-Object WorkingSet64 -Descending | Select-Object -First 5 Name, @{N='CPU(s)';E={\$_.CPU}}, @{N='Mem(MB)';E={[math]::Round(\$_.WorkingSet64/1MB,1)}} | Format-Table -AutoSize" 2>/dev/null

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Windows系统状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Windows-磁盘空间监控',
    'type': 'Windows服务器',
    'interpreter': 'sh',
    'desc': '检查Windows各磁盘分区可用空间，超过阈值告警',
    'body': r'''#!/bin/bash
# Windows磁盘空间监控
THRESHOLD_DISK="${THRESHOLD_DISK:-90}"
PS_CMD="powershell -NoProfile -Command"
HAS_ERROR=0

$PS_CMD "Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=3' | ForEach-Object { \
    \$usedPct=[math]::Round((\$_.Size-\$_.FreeSpace)/\$_.Size*100,1); \
    \$freeGB=[math]::Round(\$_.FreeSpace/1GB,1); \
    Write-Output \"\$(\$_.DeviceID) 已用\${usedPct}% 可用\${freeGB}GB\"; \
    if(\$usedPct -gt $THRESHOLD_DISK) { Write-Output 'WARNING' } \
}" 2>/dev/null | while read -r line; do
    echo "$line"
    [[ "$line" == *WARNING* ]] && HAS_ERROR=1
done

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 磁盘空间正常"
exit 0
''',
})

# ============================================================
# 五、数据库监控
# ============================================================

TEMPLATES.append({
    'name': 'MySQL-综合状态巡检',
    'type': '数据库',
    'interpreter': 'sh',
    'desc': 'MySQL综合巡检：连接数/QPS/慢查询/主从延迟/InnoDB状态/锁等待',
    'body': r'''#!/bin/bash
# MySQL综合状态巡检
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
THRESHOLD_CONN="${THRESHOLD_CONN:-80}"
THRESHOLD_SLOW="${THRESHOLD_SLOW:-10}"

MYSQL_CMD="mysql -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER"
[ -n "$MYSQL_PASS" ] && MYSQL_CMD="$MYSQL_CMD -p$MYSQL_PASS"

HAS_ERROR=0

echo "=== MySQL版本 ==="
$MYSQL_CMD -e "SELECT VERSION();" 2>/dev/null || { echo "CRITICAL: MySQL连接失败"; exit 1; }

echo "=== 连接数 ==="
CONN_INFO=$($MYSQL_CMD -e "SHOW STATUS LIKE 'Threads_connected';" 2>/dev/null | tail -1)
MAX_CONN=$($MYSQL_CMD -e "SHOW VARIABLES LIKE 'max_connections';" 2>/dev/null | tail -1 | awk '{print $2}')
CURR_CONN=$(echo "$CONN_INFO" | awk '{print $2}')
echo "当前连接: $CURR_CONN / 最大: $MAX_CONN"
if [ -n "$CURR_CONN" ] && [ -n "$MAX_CONN" ] && [ "$MAX_CONN" -gt 0 ] 2>/dev/null; then
    CONN_PCT=$((CURR_CONN * 100 / MAX_CONN))
    echo "连接使用率: ${CONN_PCT}%"
    [ "$CONN_PCT" -gt "$THRESHOLD_CONN" ] && echo "WARNING: 连接使用率超过${THRESHOLD_CONN}%" && HAS_ERROR=1
fi

echo "=== QPS/TPS ==="
QPS=$($MYSQL_CMD -e "SHOW STATUS LIKE 'Queries';" 2>/dev/null | tail -1 | awk '{print $2}')
TPS=$($MYSQL_CMD -e "SHOW STATUS LIKE 'Com_commit';" 2>/dev/null | tail -1 | awk '{print $2}')
echo "累计Queries: $QPS, 累计Commits: $TPS"

echo "=== 慢查询 ==="
SLOW_COUNT=$($MYSQL_CMD -e "SHOW STATUS LIKE 'Slow_queries';" 2>/dev/null | tail -1 | awk '{print $2}')
SLOW_TIME=$($MYSQL_CMD -e "SHOW VARIABLES LIKE 'long_query_time';" 2>/dev/null | tail -1 | awk '{print $2}')
echo "慢查询数: $SLOW_COUNT (阈值: ${SLOW_TIME}s)"

echo "=== InnoDB状态 ==="
$MYSQL_CMD -e "SHOW ENGINE INNODB STATUS\G" 2>/dev/null | grep -E "BUFFER POOL|OS file reads|OS file writes|ROW OPERATIONS" | head -10

echo "=== 主从状态 ==="
SLAVE_STATUS=$($MYSQL_CMD -e "SHOW SLAVE STATUS\G" 2>/dev/null)
if [ -n "$SLAVE_STATUS" ]; then
    IO_RUNNING=$(echo "$SLAVE_STATUS" | grep "Slave_IO_Running" | awk '{print $2}')
    SQL_RUNNING=$(echo "$SLAVE_STATUS" | grep "Slave_SQL_Running" | awk '{print $2}')
    BEHIND=$(echo "$SLAVE_STATUS" | grep "Seconds_Behind_Master" | awk '{print $2}')
    echo "IO线程: $IO_RUNNING, SQL线程: $SQL_RUNNING, 延迟: ${BEHIND}s"
    [ "$IO_RUNNING" != "Yes" ] || [ "$SQL_RUNNING" != "Yes" ] && echo "WARNING: 主从复制异常" && HAS_ERROR=1
    [ "${BEHIND:-0}" -gt 60 ] 2>/dev/null && echo "WARNING: 主从延迟超过60秒" && HAS_ERROR=1
else
    echo "非从库或未配置主从"
fi

echo "=== 锁等待 ==="
LOCK_WAIT=$($MYSQL_CMD -e "SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS;" 2>/dev/null | tail -1)
[ -n "$LOCK_WAIT" ] && echo "锁等待数: $LOCK_WAIT"

echo "=== 数据库大小 ==="
$MYSQL_CMD -e "SELECT table_schema AS db, ROUND(SUM(data_length+index_length)/1024/1024,1) AS size_mb FROM information_schema.tables GROUP BY table_schema ORDER BY size_mb DESC LIMIT 10;" 2>/dev/null

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: MySQL状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'PostgreSQL-综合状态巡检',
    'type': '数据库',
    'interpreter': 'sh',
    'desc': 'PostgreSQL综合巡检：连接数/事务/锁/复制/缓存命中率/死元组',
    'body': r'''#!/bin/bash
# PostgreSQL综合状态巡检
PG_HOST="${PG_HOST:-127.0.0.1}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
PG_DB="${PG_DB:-postgres}"
THRESHOLD_CONN="${THRESHOLD_CONN:-80}"

PG_CMD="psql -h$PG_HOST -p$PG_PORT -U$PG_USER -d$PG_DB -t -A"
HAS_ERROR=0

echo "=== PostgreSQL版本 ==="
$PG_CMD -c "SELECT version();" 2>/dev/null || { echo "CRITICAL: PostgreSQL连接失败"; exit 1; }

echo "=== 连接数 ==="
CURR_CONN=$($PG_CMD -c "SELECT count(*) FROM pg_stat_activity;" 2>/dev/null)
MAX_CONN=$($PG_CMD -c "SHOW max_connections;" 2>/dev/null)
echo "当前连接: ${CURR_CONN:-0} / 最大: ${MAX_CONN:-0}"
if [ -n "$CURR_CONN" ] && [ -n "$MAX_CONN" ] && [ "$MAX_CONN" -gt 0 ] 2>/dev/null; then
    CONN_PCT=$((CURR_CONN * 100 / MAX_CONN))
    echo "连接使用率: ${CONN_PCT}%"
    [ "$CONN_PCT" -gt "$THRESHOLD_CONN" ] && echo "WARNING: 连接使用率超过${THRESHOLD_CONN}%" && HAS_ERROR=1
fi

echo "=== 数据库大小 ==="
$PG_CMD -c "SELECT datname, pg_size_pretty(pg_database_size(datname)) FROM pg_database ORDER BY pg_database_size(datname) DESC LIMIT 10;" 2>/dev/null

echo "=== 缓存命中率 ==="
HIT_RATIO=$($PG_CMD -c "SELECT round(sum(heap_blks_hit)::numeric / nullif(sum(heap_blks_hit + heap_blks_read), 0) * 100, 2) FROM pg_statio_user_tables;" 2>/dev/null)
echo "缓存命中率: ${HIT_RATIO:-N/A}%"

echo "=== 死元组统计 ==="
$PG_CMD -c "SELECT schemaname, relname, n_dead_tup, last_vacuum, last_autovacuum FROM pg_stat_user_tables WHERE n_dead_tup > 1000 ORDER BY n_dead_tup DESC LIMIT 10;" 2>/dev/null

echo "=== 锁等待 ==="
$PG_CMD -c "SELECT count(*) FROM pg_locks WHERE NOT granted;" 2>/dev/null

echo "=== 复制状态 ==="
REPL_STATUS=$($PG_CMD -c "SELECT client_addr, state, sent_lsn, replay_lsn, (sent_lsn - replay_lsn) AS lag_bytes FROM pg_stat_replication;" 2>/dev/null)
if [ -n "$REPL_STATUS" ]; then
    echo "$REPL_STATUS"
else
    echo "非主库或未配置复制"
fi

echo "=== 活跃查询 ==="
$PG_CMD -c "SELECT pid, now()-pg_stat_activity.query_start AS duration, query FROM pg_stat_activity WHERE state='active' AND query NOT LIKE '%pg_stat_activity%' ORDER BY duration DESC LIMIT 5;" 2>/dev/null

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: PostgreSQL状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'MariaDB-综合状态巡检',
    'type': '数据库',
    'interpreter': 'sh',
    'desc': 'MariaDB综合巡检：连接数/线程/慢查询/InnoDB缓冲池/复制状态',
    'body': r'''#!/bin/bash
# MariaDB综合状态巡检
MDB_HOST="${MDB_HOST:-127.0.0.1}"
MDB_PORT="${MDB_PORT:-3306}"
MDB_USER="${MDB_USER:-root}"
MDB_PASS="${MDB_PASS:-}"

MDB_CMD="mysql -h$MDB_HOST -P$MDB_PORT -u$MDB_USER"
[ -n "$MDB_PASS" ] && MDB_CMD="$MDB_CMD -p$MDB_PASS"

HAS_ERROR=0

echo "=== MariaDB版本 ==="
$MDB_CMD -e "SELECT VERSION();" 2>/dev/null || { echo "CRITICAL: MariaDB连接失败"; exit 1; }

echo "=== 连接数 ==="
CURR_CONN=$($MDB_CMD -e "SHOW STATUS LIKE 'Threads_connected';" 2>/dev/null | tail -1 | awk '{print $2}')
MAX_CONN=$($MDB_CMD -e "SHOW VARIABLES LIKE 'max_connections';" 2>/dev/null | tail -1 | awk '{print $2}')
echo "当前连接: $CURR_CONN / 最大: $MAX_CONN"
CONN_PCT=$((CURR_CONN * 100 / MAX_CONN)) 2>/dev/null
echo "连接使用率: ${CONN_PCT}%"
[ "${CONN_PCT:-0}" -gt 80 ] 2>/dev/null && echo "WARNING: 连接使用率超过80%" && HAS_ERROR=1

echo "=== 线程状态 ==="
$MDB_CMD -e "SHOW STATUS LIKE 'Threads%';" 2>/dev/null

echo "=== 慢查询 ==="
SLOW=$($MDB_CMD -e "SHOW STATUS LIKE 'Slow_queries';" 2>/dev/null | tail -1 | awk '{print $2}')
echo "慢查询数: $SLOW"

echo "=== InnoDB缓冲池 ==="
$MDB_CMD -e "SHOW STATUS LIKE 'Innodb_buffer_pool%';" 2>/dev/null | grep -E "read|write|pages" | head -10

echo "=== 主从状态 ==="
SLAVE=$($MDB_CMD -e "SHOW SLAVE STATUS\G" 2>/dev/null)
if [ -n "$SLAVE" ]; then
    echo "$SLAVE" | grep -E "Slave_IO_Running|Slave_SQL_Running|Seconds_Behind"
else
    echo "非从库"
fi

echo "=== 数据库大小 ==="
$MDB_CMD -e "SELECT table_schema, ROUND(SUM(data_length+index_length)/1024/1024,1) AS mb FROM information_schema.tables GROUP BY table_schema ORDER BY mb DESC LIMIT 10;" 2>/dev/null

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: MariaDB状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'MySQL-主从复制监控',
    'type': '数据库',
    'interpreter': 'sh',
    'desc': '专门监控MySQL/MariaDB主从复制状态和延迟',
    'body': r'''#!/bin/bash
# MySQL主从复制监控
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-}"
THRESHOLD_BEHIND="${THRESHOLD_BEHIND:-60}"

MYSQL_CMD="mysql -h$MYSQL_HOST -P$MYSQL_PORT -u$MYSQL_USER"
[ -n "$MYSQL_PASS" ] && MYSQL_CMD="$MYSQL_CMD -p$MYSQL_PASS"

SLAVE_STATUS=$($MYSQL_CMD -e "SHOW SLAVE STATUS\G" 2>/dev/null)
if [ -z "$SLAVE_STATUS" ]; then
    echo "UNKNOWN: 非从库或未配置主从复制"
    exit 0
fi

IO_RUNNING=$(echo "$SLAVE_STATUS" | grep "Slave_IO_Running:" | awk '{print $2}')
SQL_RUNNING=$(echo "$SLAVE_STATUS" | grep "Slave_SQL_Running:" | awk '{print $2}')
BEHIND=$(echo "$SLAVE_STATUS" | grep "Seconds_Behind_Master:" | awk '{print $2}')
LAST_ERRNO=$(echo "$SLAVE_STATUS" | grep "Last_Errno:" | awk '{print $2}')
LAST_ERROR=$(echo "$SLAVE_STATUS" | grep "Last_Error:" | awk -F': ' '{$1=""; print}')

echo "IO线程: $IO_RUNNING"
echo "SQL线程: $SQL_RUNNING"
echo "延迟秒数: ${BEHIND:-NULL}"

if [ "$IO_RUNNING" != "Yes" ] || [ "$SQL_RUNNING" != "Yes" ]; then
    echo "CRITICAL: 复制线程异常 IO=$IO_RUNNING SQL=$SQL_RUNNING"
    [ "$LAST_ERRNO" != "0" ] && echo "错误码: $LAST_ERRNO, 错误: $LAST_ERROR"
    exit 1
fi

if [ "${BEHIND:-0}" -gt "$THRESHOLD_BEHIND" ] 2>/dev/null; then
    echo "WARNING: 主从延迟 ${BEHIND}s 超过阈值 ${THRESHOLD_BEHIND}s"
    exit 1
fi

echo "OK: 主从复制正常，延迟${BEHIND}s"
exit 0
''',
})

# ============================================================
# 六、中间件监控
# ============================================================

TEMPLATES.append({
    'name': 'Nginx-综合状态监控',
    'type': '中间件',
    'interpreter': 'sh',
    'desc': '监控Nginx状态：stub_status、连接数、配置语法、进程状态、虚拟主机',
    'body': r'''#!/bin/bash
# Nginx综合状态监控
NGINX_STATUS_URL="${NGINX_STATUS_URL:-http://127.0.0.1/nginx_status}"
THRESHOLD_CONN="${THRESHOLD_CONN:-500}"
HAS_ERROR=0

echo "=== Nginx进程状态 ==="
NGINX_PID=$(cat /var/run/nginx.pid 2>/dev/null || pgrep -o nginx)
if [ -z "$NGINX_PID" ]; then
    echo "CRITICAL: Nginx进程不存在"
    exit 1
fi
echo "主进程PID: $NGINX_PID"
WORKER_COUNT=$(pgrep -c nginx)
echo "Worker进程数: $WORKER_COUNT"

echo "=== 配置语法检查 ==="
nginx -t 2>&1
if [ $? -ne 0 ]; then
    echo "CRITICAL: Nginx配置语法错误"
    exit 1
fi

echo "=== Stub Status ==="
STATUS=$(curl -s "$NGINX_STATUS_URL" 2>/dev/null)
if [ -n "$STATUS" ]; then
    ACTIVE=$(echo "$STATUS" | grep "Active connections" | awk '{print $3}')
    ACCEPTS=$(echo "$STATUS" | awk 'NR==3{print $1}')
    HANDLED=$(echo "$STATUS" | awk 'NR==3{print $2}')
    REQUESTS=$(echo "$STATUS" | awk 'NR==3{print $3}')
    READING=$(echo "$STATUS" | grep "Reading" | awk '{print $2}')
    WRITING=$(echo "$STATUS" | grep "Reading" | awk '{print $4}')
    WAITING=$(echo "$STATUS" | grep "Reading" | awk '{print $6}')
    echo "活跃连接: $ACTIVE"
    echo "请求统计: 接受=$ACCEPTS 处理=$HANDLED 总请求=$REQUESTS"
    echo "连接状态: Reading=$READING Writing=$WRITING Waiting=$WAITING"
    [ "$ACTIVE" -gt "$THRESHOLD_CONN" ] 2>/dev/null && echo "WARNING: 活跃连接数超过${THRESHOLD_CONN}" && HAS_ERROR=1
else
    echo "NOTICE: Stub Status不可用，请配置nginx_status模块"
fi

echo "=== 监听端口 ==="
ss -tlnp 2>/dev/null | grep nginx || netstat -tlnp 2>/dev/null | grep nginx

echo "=== 虚拟主机 ==="
VHOST_COUNT=$(grep -r "server_name" /etc/nginx/ 2>/dev/null | grep -v "#" | wc -l)
echo "虚拟主机配置数: $VHOST_COUNT"

echo "=== 错误日志 ==="
tail -5 /var/log/nginx/error.log 2>/dev/null || echo "错误日志不可读"

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Nginx状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Apache-httpd综合状态监控',
    'type': '中间件',
    'interpreter': 'sh',
    'desc': '监控Apache httpd状态：server-status、进程数、连接数、模块',
    'body': r'''#!/bin/bash
# Apache httpd综合状态监控
APACHE_STATUS_URL="${APACHE_STATUS_URL:-http://127.0.0.1/server-status?auto}"
THRESHOLD_BUSY="${THRESHOLD_BUSY:-80}"
HAS_ERROR=0

echo "=== Apache进程状态 ==="
HTTPD_PROCS=$(pgrep -c httpd 2>/dev/null || pgrep -c apache2 2>/dev/null)
if [ "$HTTPD_PROCS" -eq 0 ] 2>/dev/null; then
    echo "CRITICAL: Apache进程不存在"
    exit 1
fi
echo "进程数: $HTTPD_PROCS"

echo "=== 配置语法检查 ==="
(apache2ctl -t 2>&1 || httpd -t 2>&1)
if [ $? -ne 0 ]; then
    echo "CRITICAL: Apache配置语法错误"
    exit 1
fi

echo "=== Server Status ==="
STATUS=$(curl -s "$APACHE_STATUS_URL" 2>/dev/null)
if [ -n "$STATUS" ]; then
    echo "$STATUS" | grep -E "Total Accesses|Total kBytes|Uptime|BusyWorkers|IdleWorkers|Scoreboard"
    BUSY=$(echo "$STATUS" | grep "BusyWorkers" | awk '{print $2}')
    IDLE=$(echo "$STATUS" | grep "IdleWorkers" | awk '{print $2}')
    if [ -n "$BUSY" ] && [ -n "$IDLE" ]; then
        TOTAL=$((BUSY + IDLE))
        if [ "$TOTAL" -gt 0 ] 2>/dev/null; then
            BUSY_PCT=$((BUSY * 100 / TOTAL))
            echo "Worker使用率: ${BUSY_PCT}% (${BUSY}/${TOTAL})"
            [ "$BUSY_PCT" -gt "$THRESHOLD_BUSY" ] && echo "WARNING: Worker使用率超过${THRESHOLD_BUSY}%" && HAS_ERROR=1
        fi
    fi
else
    echo "NOTICE: server-status不可用，请启用mod_status"
fi

echo "=== 监听端口 ==="
ss -tlnp 2>/dev/null | grep -E "httpd|apache" || netstat -tlnp 2>/dev/null | grep -E "httpd|apache"

echo "=== 已加载模块 ==="
(apache2ctl -M 2>/dev/null || httpd -M 2>/dev/null) | head -20

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Apache状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Redis-综合状态巡检',
    'type': '中间件',
    'interpreter': 'sh',
    'desc': 'Redis综合巡检：内存/连接/命令统计/主从/慢日志/键空间',
    'body': r'''#!/bin/bash
# Redis综合状态巡检
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASS="${REDIS_PASS:-}"
REDIS_CLI="redis-cli -h $REDIS_HOST -p $REDIS_PORT"
[ -n "$REDIS_PASS" ] && REDIS_CLI="$REDIS_CLI -a $REDIS_PASS"

HAS_ERROR=0

echo "=== Redis信息 ==="
INFO=$($REDIS_CLI INFO server 2>/dev/null)
if [ -z "$INFO" ]; then
    echo "CRITICAL: Redis连接失败"
    exit 1
fi
echo "$INFO" | grep -E "redis_version|uptime_in_days|os"

echo "=== 内存使用 ==="
MEM_INFO=$($REDIS_CLI INFO memory 2>/dev/null)
echo "$MEM_INFO" | grep -E "used_memory_human|used_memory_peak_human|maxmemory_human|mem_fragmentation_ratio"

echo "=== 客户端连接 ==="
CLIENTS_INFO=$($REDIS_CLI INFO clients 2>/dev/null)
echo "$CLIENTS_INFO" | grep -E "connected_clients|blocked_clients"

echo "=== 命令统计 ==="
$REDIS_CLI INFO stats 2>/dev/null | grep -E "total_connections|total_commands|instantaneous_ops|keyspace_hits|keyspace_misses"

echo "=== 主从状态 ==="
REPL_INFO=$($REDIS_CLI INFO replication 2>/dev/null)
echo "$REPL_INFO" | grep -E "role|connected_slaves|master_repl_offset"

echo "=== 慢日志 ==="
$REDIS_CLI SLOWLOG GET 5 2>/dev/null

echo "=== 键空间 ==="
$REDIS_CLI INFO keyspace 2>/dev/null | grep -v "^$"

echo "=== 持久化 ==="
$REDIS_CLI INFO persistence 2>/dev/null | grep -E "rdb_last_save_time|aof_enabled|rdb_changes_since_last_save"

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Redis状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Tomcat-应用服务监控',
    'type': '中间件',
    'interpreter': 'sh',
    'desc': '监控Tomcat应用服务器：线程池、JVM内存、请求处理、HTTP状态',
    'body': r'''#!/bin/bash
# Tomcat应用服务监控
TOMCAT_STATUS_URL="${TOMCAT_STATUS_URL:-http://127.0.0.1:8080/manager/status?XML=true}"
TOMCAT_USER="${TOMCAT_USER:-admin}"
TOMCAT_PASS="${TOMCAT_PASS:-}"
THRESHOLD_THREADS="${THRESHOLD_THREADS:-90}"

HAS_ERROR=0

echo "=== Tomcat进程 ==="
TOMCAT_PID=$(pgrep -f "catalina" | head -1)
if [ -z "$TOMCAT_PID" ]; then
    echo "CRITICAL: Tomcat进程不存在"
    exit 1
fi
echo "PID: $TOMCAT_PID"

echo "=== JVM内存 ==="
if [ -n "$TOMCAT_PID" ]; then
    HEAP_USED=$(jstat -gc "$TOMCAT_PID" 2>/dev/null | tail -1 | awk '{print $3+$4+$6+$8}')
    echo "JVM堆使用(KB): ${HEAP_USED:-N/A}"
fi

echo "=== HTTP端口检测 ==="
TOMCAT_PORT=$(ss -tlnp 2>/dev/null | grep "$TOMCAT_PID" | head -1 | awk '{print $4}' | rev | cut -d: -f1 | rev)
if [ -n "$TOMCAT_PORT" ]; then
    echo "监听端口: $TOMCAT_PORT"
    curl -s -o /dev/null -w "HTTP状态码: %{http_code}\n" "http://127.0.0.1:$TOMCAT_PORT/" 2>/dev/null
else
    echo "WARNING: 未检测到Tomcat监听端口"
fi

echo "=== 线程状态 ==="
THREAD_COUNT=$(ls /proc/$TOMCAT_PID/task 2>/dev/null | wc -l)
echo "线程数: ${THREAD_COUNT:-N/A}"

echo "=== 打开文件描述符 ==="
FD_COUNT=$(ls /proc/$TOMCAT_PID/fd 2>/dev/null | wc -l)
echo "文件描述符: ${FD_COUNT:-N/A}"

echo "=== GC统计 ==="
if [ -n "$TOMCAT_PID" ]; then
    jstat -gcutil "$TOMCAT_PID" 2>/dev/null | head -5
fi

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Tomcat状态正常"
exit 0
''',
})

# ============================================================
# 七、业务应用监控
# ============================================================

TEMPLATES.append({
    'name': 'Web服务-HTTP可用性检测',
    'type': '业务应用',
    'interpreter': 'sh',
    'desc': '检测Web服务的HTTP可用性、响应时间、SSL证书有效期',
    'body': r'''#!/bin/bash
# Web服务HTTP可用性检测
WEB_URL="${WEB_URL:-http://127.0.0.1}"
THRESHOLD_MS="${THRESHOLD_MS:-3000}"
THRESHOLD_SSL_DAYS="${THRESHOLD_SSL_DAYS:-30}"
HAS_ERROR=0

echo "=== HTTP检测 ==="
RESP=$(curl -s -o /dev/null -w "%{http_code}|%{time_total}|%{ssl_verify_result}" --max-time 10 "$WEB_URL" 2>/dev/null)
HTTP_CODE=$(echo "$RESP" | cut -d'|' -f1)
TIME_TOTAL=$(echo "$RESP" | cut -d'|' -f2)
TIME_MS=$(echo "$TIME_TOTAL * 1000" | bc 2>/dev/null | cut -d'.' -f1)

echo "URL: $WEB_URL"
echo "HTTP状态码: $HTTP_CODE"
echo "响应时间: ${TIME_MS:-N/A}ms"

if [ "$HTTP_CODE" -lt 200 ] || [ "$HTTP_CODE" -ge 400 ] 2>/dev/null; then
    echo "CRITICAL: HTTP状态码异常 $HTTP_CODE"
    HAS_ERROR=1
fi

if [ -n "$TIME_MS" ] && [ "$TIME_MS" -gt "$THRESHOLD_MS" ] 2>/dev/null; then
    echo "WARNING: 响应时间 ${TIME_MS}ms 超过阈值 ${THRESHOLD_MS}ms"
    HAS_ERROR=1
fi

echo "=== SSL证书检测 ==="
if [[ "$WEB_URL" == https://* ]]; then
    CERT_INFO=$(echo | openssl s_client -servername $(echo "$WEB_URL" | awk -F/ '{print $3}') -connect $(echo "$WEB_URL" | awk -F/ '{print $3}'):443 2>/dev/null | openssl x509 -noout -dates -subject 2>/dev/null)
    if [ -n "$CERT_INFO" ]; then
        echo "$CERT_INFO"
        EXPIRE_DATE=$(echo "$CERT_INFO" | grep "notAfter" | cut -d= -f2)
        EXPIRE_EPOCH=$(date -d "$EXPIRE_DATE" +%s 2>/dev/null)
        NOW_EPOCH=$(date +%s)
        if [ -n "$EXPIRE_EPOCH" ]; then
            DAYS_LEFT=$(( (EXPIRE_EPOCH - NOW_EPOCH) / 86400 ))
            echo "证书剩余天数: $DAYS_LEFT"
            [ "$DAYS_LEFT" -lt "$THRESHOLD_SSL_DAYS" ] && echo "WARNING: SSL证书将在${DAYS_LEFT}天后过期" && HAS_ERROR=1
        fi
    else
        echo "NOTICE: 无法获取SSL证书信息"
    fi
fi

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Web服务正常"
exit 0
''',
})

TEMPLATES.append({
    'name': 'Docker容器-服务状态监控',
    'type': '业务应用',
    'interpreter': 'sh',
    'desc': '监控Docker容器运行状态、资源使用、健康检查',
    'body': r'''#!/bin/bash
# Docker容器服务状态监控
THRESHOLD_CPU="${THRESHOLD_CPU:-90}"
THRESHOLD_MEM_PCT="${THRESHOLD_MEM_PCT:-90}"
HAS_ERROR=0

if ! command -v docker &>/dev/null; then
    echo "UNKNOWN: Docker未安装"
    exit 0
fi

echo "=== Docker服务状态 ==="
docker info 2>/dev/null | grep -E "Server Version|Containers|Running|Paused|Stopped|Images"

echo "=== 容器状态 ==="
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null | head -20

echo "=== 异常容器 ==="
EXITED=$(docker ps -a --filter "status=exited" -q 2>/dev/null | wc -l)
RESTARTING=$(docker ps -a --filter "status=restarting" -q 2>/dev/null | wc -l)
UNHEALTHY=$(docker ps -a --filter "health=unhealthy" -q 2>/dev/null | wc -l)
echo "已退出: $EXITED, 重启中: $RESTARTING, 不健康: $UNHEALTHY"
[ "$UNHEALTHY" -gt 0 ] 2>/dev/null && echo "WARNING: 存在不健康容器" && HAS_ERROR=1

echo "=== 容器资源使用 ==="
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" 2>/dev/null | while read -r line; do
    echo "$line"
    MEM_PCT=$(echo "$line" | awk '{print $4}' | tr -d '%' 2>/dev/null)
    if [ -n "$MEM_PCT" ] && [ "$MEM_PCT" -gt "$THRESHOLD_MEM_PCT" ] 2>/dev/null; then
        echo "WARNING: 容器内存使用率超过${THRESHOLD_MEM_PCT}%"
        HAS_ERROR=1
    fi
done

echo "=== 磁盘使用 ==="
docker system df 2>/dev/null

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Docker状态正常"
exit 0
''',
})

TEMPLATES.append({
    'name': '定时任务-系统Cron巡检',
    'type': '业务应用',
    'interpreter': 'sh',
    'desc': '检查系统crontab任务状态、最近执行结果、异常任务',
    'body': r'''#!/bin/bash
# 系统Cron定时任务巡检
HAS_ERROR=0

echo "=== Crond服务状态 ==="
if systemctl is-active crond &>/dev/null || systemctl is-active cron &>/dev/null; then
    echo "Cron服务: 运行中"
else
    echo "WARNING: Cron服务未运行"
    HAS_ERROR=1
fi

echo "=== 系统定时任务 ==="
echo "--- /etc/crontab ---"
cat /etc/crontab 2>/dev/null | grep -v "^#" | grep -v "^$"
echo ""
echo "--- /etc/cron.d/ ---"
ls -la /etc/cron.d/ 2>/dev/null
for f in /etc/cron.d/*; do
    [ -f "$f" ] && echo ">>> $f" && grep -v "^#" "$f" | grep -v "^$"
done

echo "=== 用户定时任务 ==="
for user in $(cut -d: -f1 /etc/passwd); do
    CRON=$(crontab -u "$user" -l 2>/dev/null | grep -v "^#" | grep -v "^$")
    if [ -n "$CRON" ]; then
        echo "--- $user ---"
        echo "$CRON"
    fi
done

echo "=== Cron日志(最近错误) ==="
grep -i "error\|failed" /var/log/cron 2>/dev/null | tail -5 || journalctl -u crond --since "1 hour ago" 2>/dev/null | grep -i "error\|failed" | tail -5

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: Cron巡检完成"
exit 0
''',
})

TEMPLATES.append({
    'name': 'NTP时间同步监控',
    'type': '业务应用',
    'interpreter': 'sh',
    'desc': '检查NTP时间同步状态、时钟偏移',
    'body': r'''#!/bin/bash
# NTP时间同步监控
THRESHOLD_OFFSET="${THRESHOLD_OFFSET:-100}"
HAS_ERROR=0

echo "=== NTP服务状态 ==="
if systemctl is-active chronyd &>/dev/null; then
    echo "Chrony: 运行中"
    chronyc tracking 2>/dev/null | grep -E "Reference|Stratum|Last offset|RMS offset|Leap status"
    OFFSET=$(chronyc tracking 2>/dev/null | grep "Last offset" | awk '{print $4}')
elif systemctl is-active ntpd &>/dev/null; then
    echo "NTPd: 运行中"
    ntpq -p 2>/dev/null | head -10
    OFFSET=$(ntpq -p 2>/dev/null | grep "^\*" | awk '{print $9}')
else
    echo "WARNING: NTP服务未运行"
    HAS_ERROR=1
fi

if [ -n "$OFFSET" ]; then
    OFFSET_INT=$(echo "$OFFSET" | cut -d'.' -f1 | tr -d '-')
    echo "时钟偏移: ${OFFSET}ms"
    [ "$OFFSET_INT" -gt "$THRESHOLD_OFFSET" ] 2>/dev/null && echo "WARNING: 时钟偏移超过${THRESHOLD_OFFSET}ms" && HAS_ERROR=1
fi

echo "=== 当前时间 ==="
echo "系统时间: $(date)"
echo "硬件时间: $(hwclock 2>/dev/null || echo 'N/A')"

[ $HAS_ERROR -eq 1 ] && exit 1
echo "OK: 时间同步正常"
exit 0
''',
})

# ============================================================
# 执行写入
# ============================================================

def main():
    existing = {(t.name, t.type) for t in ExecTemplate.objects.all()}
    created = 0
    for tpl in TEMPLATES:
        key = (tpl['name'], tpl['type'])
        if key in existing:
            print(f"  SKIP: {tpl['name']} [{tpl['type']}] - 已存在")
            continue
        ExecTemplate.objects.create(
            name=tpl['name'],
            type=tpl['type'],
            body=tpl['body'],
            interpreter=tpl.get('interpreter', 'sh'),
            desc=tpl.get('desc', ''),
            host_ids='[]',
            parameters='[]',
            created_by=ADMIN,
        )
        created += 1
        print(f"  OK: {tpl['name']} [{tpl['type']}]")

    total = ExecTemplate.objects.count()
    print(f"\n完成! 新建 {created} 个模板, 数据库共 {total} 个模板")

if __name__ == '__main__':
    main()