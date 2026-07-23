#!/bin/bash
BASE="http://localhost"

echo "=== 测试1: 基本登录 ==="
curl -s -X POST $BASE/api/account/login/ \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Spug@2024"}'

echo ""
echo "=== 测试2: 带type参数登录 ==="
curl -s -X POST $BASE/api/account/login/ \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Spug@2024","type":"default"}'

echo ""
echo "=== 测试3: 用文件发送JSON ==="
echo '{"username":"admin","password":"Spug@2024"}' > /tmp/login.json
curl -s -X POST $BASE/api/account/login/ \
  -H 'Content-Type: application/json' \
  -d @/tmp/login.json

echo ""
echo "=== 测试4: 检查请求体 ==="
curl -s -X POST $BASE/api/account/login/ \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/login.json