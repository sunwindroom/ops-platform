#!/bin/bash
BASE="http://localhost"
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
echo "  Spug运维平台 - 部署验证测试"
echo "  测试时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  服务器: 192.168.10.203:80"
echo "============================================"
echo ""

echo "--- 一、基础连通性测试 ---"
STATUS=$(curl -s -o /dev/null -w '%{http_code}' $BASE/)
check "Spug首页可访问" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' $BASE/index.html)
check "index.html可访问" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w '%{http_code}' $BASE/api/account/login/)
check "API登录接口可访问" "200" "$STATUS"

echo ""
echo "--- 二、登录认证测试 ---"
LOGIN_RESP=$(curl -s -X POST $BASE/api/account/login/ -H 'Content-Type: application/json' -d '{"username":"admin","password":"Spug@2024","type":"default"}')
if echo "$LOGIN_RESP" | grep -q 'access_token'; then
    echo "[PASS] 管理员登录成功，获取到token"
    PASS=$((PASS+1))
    TOKEN=$(echo "$LOGIN_RESP" | python -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])" 2>/dev/null)
else
    echo "[FAIL] 管理员登录失败: $(echo $LOGIN_RESP | head -c 200)"
    FAIL=$((FAIL+1))
    TOKEN=""
fi

BAD_LOGIN=$(curl -s -X POST $BASE/api/account/login/ -H 'Content-Type: application/json' -d '{"username":"admin","password":"wrongpass","type":"default"}')
if echo "$BAD_LOGIN" | grep -q 'error'; then
    echo "[PASS] 错误密码登录被拒绝"
    PASS=$((PASS+1))
else
    echo "[FAIL] 错误密码登录未被拒绝"
    FAIL=$((FAIL+1))
fi

echo ""
echo "--- 三、API功能测试(需认证) ---"
if [ -n "$TOKEN" ]; then
    AUTH="x-token: $TOKEN"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/host/)
    check "主机管理API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/host/group/)
    check "主机组API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/app/)
    check "应用管理API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/monitor/)
    check "监控中心API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/schedule/)
    check "任务计划API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/alarm/group/)
    check "告警分组API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/config/service/)
    check "配置中心API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/account/role/)
    check "角色管理API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/setting/)
    check "系统设置API" "200" "$STATUS"

    STATUS=$(curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/repository/)
    check "仓库管理API" "200" "$STATUS"

    # 创建主机组 (error:null表示成功)
    CREATE_GROUP=$(curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' $BASE/api/host/group/ -d '{"name":"auto-test-group"}')
    if echo "$CREATE_GROUP" | python -c "import sys,json; d=json.load(sys.stdin); exit(0 if d.get('error') is None or d.get('error')=='' else 1)" 2>/dev/null; then
        echo "[PASS] 创建主机组成功"
        PASS=$((PASS+1))
    else
        echo "[FAIL] 创建主机组失败: $(echo $CREATE_GROUP | head -c 200)"
        FAIL=$((FAIL+1))
    fi

    # 创建主机
    CREATE_HOST=$(curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' $BASE/api/host/ -d '{"name":"auto-test-server","hostname":"192.168.10.203","port":22,"username":"root","group_ids":[1]}')
    if echo "$CREATE_HOST" | python -c "import sys,json; d=json.load(sys.stdin); exit(0 if d.get('error') is None or d.get('error')=='' else 1)" 2>/dev/null; then
        echo "[PASS] 创建主机成功"
        PASS=$((PASS+1))
    else
        echo "[FAIL] 创建主机失败: $(echo $CREATE_HOST | head -c 200)"
        FAIL=$((FAIL+1))
    fi

    # 获取主机列表
    HOST_LIST=$(curl -s -H "$AUTH" $BASE/api/host/)
    HOST_COUNT=$(echo "$HOST_LIST" | python -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])))" 2>/dev/null || echo "0")
    if [ "$HOST_COUNT" -ge "1" ] 2>/dev/null; then
        echo "[PASS] 主机列表有数据: $HOST_COUNT 条"
        PASS=$((PASS+1))
    else
        echo "[FAIL] 主机列表无数据"
        FAIL=$((FAIL+1))
    fi

    # 创建应用
    CREATE_APP=$(curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' $BASE/api/app/ -d '{"name":"auto-test-app","key":"auto_test_key_001"}')
    if echo "$CREATE_APP" | python -c "import sys,json; d=json.load(sys.stdin); exit(0 if d.get('error') is None or d.get('error')=='' else 1)" 2>/dev/null; then
        echo "[PASS] 创建应用成功"
        PASS=$((PASS+1))
    else
        echo "[FAIL] 创建应用失败: $(echo $CREATE_APP | head -c 200)"
        FAIL=$((FAIL+1))
    fi

    # 未认证访问API
    NO_AUTH_STATUS=$(curl -s -o /dev/null -w '%{http_code}' $BASE/api/host/)
    if [ "$NO_AUTH_STATUS" = "401" ] || [ "$NO_AUTH_STATUS" = "403" ]; then
        echo "[PASS] 未认证访问API被拒绝($NO_AUTH_STATUS)"
        PASS=$((PASS+1))
    else
        echo "[FAIL] 未认证访问API未被拒绝($NO_AUTH_STATUS)"
        FAIL=$((FAIL+1))
    fi
else
    echo "[SKIP] 无token，跳过认证API测试"
    FAIL=$((FAIL+14))
fi

echo ""
echo "--- 四、Docker容器测试 ---"
SPUG_STATUS=$(docker inspect -f '{{.State.Status}}' spug 2>/dev/null)
check "Spug容器运行状态" "running" "$SPUG_STATUS"

DB_STATUS=$(docker inspect -f '{{.State.Status}}' spug-db 2>/dev/null)
check "MariaDB容器运行状态" "running" "$DB_STATUS"

PORT_MAP=$(docker port spug 80/tcp 2>/dev/null)
if echo "$PORT_MAP" | grep -q '80'; then
    echo "[PASS] Spug端口映射正确: $PORT_MAP"
    PASS=$((PASS+1))
else
    echo "[FAIL] Spug端口映射异常"
    FAIL=$((FAIL+1))
fi

echo ""
echo "--- 五、数据库验证 ---"
DB_CHECK=$(docker exec spug-db mysql -uspug -pSpug@2024 spug -e "SELECT COUNT(*) FROM users;" -s 2>/dev/null)
if [ "$DB_CHECK" -ge "1" ] 2>/dev/null; then
    echo "[PASS] 数据库用户表有数据: $DB_CHECK 条"
    PASS=$((PASS+1))
else
    echo "[FAIL] 数据库用户表无数据"
    FAIL=$((FAIL+1))
fi

TABLE_COUNT=$(docker exec spug-db mysql -uspug -pSpug@2024 spug -e "SHOW TABLES;" -s 2>/dev/null | wc -l)
echo "[INFO] 数据库表数量: $TABLE_COUNT"
if [ "$TABLE_COUNT" -ge "20" ] 2>/dev/null; then
    echo "[PASS] 数据库表数量充足: $TABLE_COUNT 张"
    PASS=$((PASS+1))
else
    echo "[FAIL] 数据库表数量不足: $TABLE_COUNT 张"
    FAIL=$((FAIL+1))
fi

echo ""
echo "--- 六、服务稳定性测试 ---"
PORT_LISTEN=$(ss -tlnp | grep ':80 ' | wc -l)
if [ "$PORT_LISTEN" -ge "1" ]; then
    echo "[PASS] 80端口监听中"
    PASS=$((PASS+1))
else
    echo "[FAIL] 80端口未监听"
    FAIL=$((FAIL+1))
fi

RESTART_POLICY=$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' spug 2>/dev/null)
check "Spug容器重启策略" "always" "$RESTART_POLICY"

DB_RESTART=$(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' spug-db 2>/dev/null)
check "MariaDB容器重启策略" "always" "$DB_RESTART"

SPUG_MEM=$(docker stats spug --no-stream --format "{{.MemUsage}}" 2>/dev/null)
echo "[INFO] Spug容器内存使用: $SPUG_MEM"
DB_MEM=$(docker stats spug-db --no-stream --format "{{.MemUsage}}" 2>/dev/null)
echo "[INFO] MariaDB容器内存使用: $DB_MEM"

echo ""
echo "============================================"
echo "  测试结果汇总"
echo "  通过: $PASS"
echo "  失败: $FAIL"
echo "  总计: $((PASS+FAIL))"
if [ $((PASS+FAIL)) -gt 0 ]; then
    echo "  通过率: $(echo "scale=1; $PASS*100/($PASS+$FAIL)" | bc)%"
fi
echo "============================================"
