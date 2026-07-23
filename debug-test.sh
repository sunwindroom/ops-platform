#!/bin/bash
BASE="http://localhost"
TOKEN=$(curl -s -X POST $BASE/api/account/login/ -H 'Content-Type: application/json' -d '{"username":"admin","password":"Spug@2024","type":"default"}' | python -c "import sys,json; print(json.load(sys.stdin)['data']['access_token'])" 2>/dev/null)
AUTH="x-token: $TOKEN"

echo "=== 创建主机(带分组ID) ==="
RESP=$(curl -s -X POST -H "$AUTH" -H 'Content-Type: application/json' $BASE/api/host/ -d '{"hostname":"test-server","ip":"192.168.10.203","port":22,"username":"root","group_ids":[1]}')
echo "$RESP"

echo ""
echo "=== 查看主机列表 ==="
curl -s -H "$AUTH" $BASE/api/host/ | python -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('data',[])), 'hosts')" 2>/dev/null
