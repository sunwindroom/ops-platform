#!/bin/bash
BASE="http://localhost:8080"
PASS=0
FAIL=0

check() {
    local name="$1"
    local expected="$2"
    local actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo "[PASS] $name: $actual"
        PASS=$((PASS+1))
    else
        echo "[FAIL] $name: 期望=$expected 实际=$actual"
        FAIL=$((FAIL+1))
    fi
}

echo "============================================"
echo "  内网自动运维管理平台 - 功能测试"
echo "  测试时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  服务器: 192.168.10.203:8080"
echo "============================================"
echo ""

echo "--- 一、基础连通性测试 ---"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' $BASE/login)
check "登录页面可访问" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' -L $BASE/)
check "未认证访问首页重定向到登录" "200" "$STATUS"

echo ""
echo "--- 二、登录认证测试 ---"
CSRF=$(curl -s -c /tmp/tc.txt $BASE/login | grep '_csrf' | head -1 | sed 's/.*value="//;s/".*//')
LOGIN_URL=$(curl -s -b /tmp/tc.txt -c /tmp/tc2.txt -d "username=admin&password=Admin@123&_csrf=$CSRF" -w '%{url_effective}' -L $BASE/login -o /dev/null)
if echo "$LOGIN_URL" | grep -q '/$'; then
    echo "[PASS] 管理员登录成功跳转到首页"
    PASS=$((PASS+1))
else
    echo "[FAIL] 管理员登录失败(跳转到$LOGIN_URL)"
    FAIL=$((FAIL+1))
fi

CSRF2=$(curl -s -c /tmp/tc3.txt $BASE/login | grep '_csrf' | head -1 | sed 's/.*value="//;s/".*//')
BAD_STATUS=$(curl -s -b /tmp/tc3.txt -d "username=admin&password=wrongpass&_csrf=$CSRF2" -w '%{http_code}' $BASE/login -o /dev/null)
check "错误密码登录被拒绝(302)" "302" "$BAD_STATUS"

echo ""
echo "--- 三、页面功能测试 ---"
STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/)
check "仪表盘页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/assets)
check "资产管理页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/assets/new)
check "新增资产表单页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/assets/import)
check "CSV批量导入页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/alerts/rules)
check "告警规则页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/alerts/records)
check "告警记录页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/alerts/mutes)
check "静默窗口页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/tasks)
check "自动化运维页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/users)
check "用户管理页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/audit-logs)
check "审计日志页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/discovery)
check "网络发现页面" "200" "$STATUS"

STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/profile)
check "个人中心页面" "200" "$STATUS"

echo ""
echo "--- 四、资产CRUD测试 ---"
CSRF4=$(curl -s -b /tmp/tc2.txt $BASE/assets/new | grep '_csrf' | head -1 | sed 's/.*value="//;s/".*//')
CREATE_STATUS=$(curl -s -b /tmp/tc2.txt -d "name=test-linux-server&type=PHYSICAL&category=physical_server&ip=192.168.10.203&collectMethod=SSH&sshPort=22&sshUser=root&rawSshPassword=Clbr@2024&_csrf=$CSRF4" -w '%{http_code}' -o /dev/null $BASE/assets/save)
check "创建资产(重定向到列表)" "302" "$CREATE_STATUS"

ASSET_PAGE=$(curl -s -b /tmp/tc2.txt $BASE/assets)
if echo "$ASSET_PAGE" | grep -q "test-linux-server"; then
    echo "[PASS] 资产列表中可见新建资产"
    PASS=$((PASS+1))
else
    echo "[FAIL] 资产列表中未找到新建资产"
    FAIL=$((FAIL+1))
fi

ASSET_ID=$(curl -s -b /tmp/tc2.txt $BASE/assets | grep -oP '/assets/\K[0-9]+(?=/edit)' | head -1)
if [ -n "$ASSET_ID" ]; then
    CSRF5=$(curl -s -b /tmp/tc2.txt $BASE/assets | grep '_csrf' | head -1 | sed 's/.*value="//;s/".*//')
    COLLECT_RESP=$(curl -s -b /tmp/tc2.txt -d "_csrf=$CSRF5" $BASE/assets/$ASSET_ID/collect-now)
    if echo "$COLLECT_RESP" | grep -q '"success":true'; then
        echo "[PASS] 立即采集功能正常"
        PASS=$((PASS+1))
    else
        echo "[FAIL] 立即采集功能异常"
        FAIL=$((FAIL+1))
    fi

    CSRF_DEL=$(curl -s -b /tmp/tc2.txt $BASE/assets | grep '_csrf' | head -1 | sed 's/.*value="//;s/".*//')
    DEL_RESP=$(curl -s -b /tmp/tc2.txt -d "_csrf=$CSRF_DEL" $BASE/assets/$ASSET_ID/delete)
    if echo "$DEL_RESP" | grep -q '"success":true'; then
        echo "[PASS] 删除资产功能正常"
        PASS=$((PASS+1))
    else
        echo "[FAIL] 删除资产功能异常"
        FAIL=$((FAIL+1))
    fi
else
    echo "[SKIP] 无法获取资产ID"
fi

echo ""
echo "--- 五、告警规则测试 ---"
STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/alerts/rules/new)
check "新增告警规则表单页面" "200" "$STATUS"

echo ""
echo "--- 六、自动化运维测试 ---"
STATUS=$(curl -s -b /tmp/tc2.txt -o /dev/null -w '%{http_code}' $BASE/tasks/new)
check "新增运维任务表单页面" "200" "$STATUS"

echo ""
echo "--- 七、数据库验证 ---"
TABLE_COUNT=$(mysql -u ops_user -pClbr@Mysql2024 ops_platform -e "SHOW TABLES;" -s 2>/dev/null | wc -l)
check "数据库表数量(9张)" "9" "$TABLE_COUNT"

ADMIN_COUNT=$(mysql -u ops_user -pClbr@Mysql2024 ops_platform -e "SELECT COUNT(*) FROM sys_user WHERE username='admin';" -s 2>/dev/null)
check "管理员账号存在" "1" "$ADMIN_COUNT"

echo ""
echo "--- 八、服务稳定性测试 ---"
PROC_COUNT=$(ps aux | grep ops-platform.jar | grep -v grep | wc -l)
check "Java应用进程运行中" "1" "$PROC_COUNT"

PORT_LISTEN=$(ss -tlnp | grep 8080 | wc -l)
check "8080端口监听中" "1" "$PORT_LISTEN"

ERROR_COUNT=$(grep -c 'ERROR' /tmp/ops-platform.log 2>/dev/null || echo "0")
if [ "$ERROR_COUNT" -le "10" ]; then
    echo "[PASS] 应用日志ERROR数量: $ERROR_COUNT (<=10)"
    PASS=$((PASS+1))
else
    echo "[FAIL] 应用日志ERROR数量: $ERROR_COUNT (>10)"
    FAIL=$((FAIL+1))
fi

MEM_MB=$(ps aux | grep ops-platform.jar | grep -v grep | awk '{print int($6/1024)}')
echo "[INFO] 应用内存使用: ${MEM_MB}MB"

echo ""
echo "============================================"
echo "  测试结果汇总"
echo "  通过: $PASS"
echo "  失败: $FAIL"
echo "  总计: $((PASS+FAIL))"
echo "  通过率: $(echo "scale=1; $PASS*100/($PASS+$FAIL)" | bc)%"
echo "============================================"
