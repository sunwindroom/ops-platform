#!/bin/bash
BASE="http://localhost"
TOKEN=$(curl -s -X POST $BASE/api/account/login/ -H 'Content-Type: application/json' -d '{"username":"admin","password":"Spug@2024","type":"default"}' | python -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])" 2>/dev/null)
AUTH="x-token: $TOKEN"

echo "Token: $TOKEN"
echo ""
echo "=== 测试API路径 ==="
echo -n "/api/host/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/host/; echo ""
echo -n "/api/app/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/app/; echo ""
echo -n "/api/monitor/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/monitor/; echo ""
echo -n "/api/schedule/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/schedule/; echo ""
echo -n "/api/setting/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/setting/; echo ""
echo -n "/api/exec/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/exec/; echo ""
echo -n "/api/deploy/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/deploy/; echo ""
echo -n "/api/repository/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/repository/; echo ""
echo -n "/api/home/: "; curl -s -o /dev/null -w '%{http_code}' -H "$AUTH" $BASE/api/home/; echo ""

echo ""
echo "=== 创建主机 ==="
curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' $BASE/api/host/ -d '{"hostname":"test-server","ip":"192.168.10.203","port":22,"username":"root"}'

echo ""
echo "=== 创建应用 ==="
curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' $BASE/api/app/ -d '{"name":"test-app","key":"test-key"}'

echo ""
echo "=== 创建监控 ==="
curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' $BASE/api/monitor/ -d '{"name":"ping-test","type":"1","target":"127.0.0.1"}'