#!/bin/bash
set -e

echo "=== Step 1: Configure Docker mirror ==="
mkdir -p /etc/docker
cat > /etc/docker/daemon.json << 'EOF'
{
  "registry-mirrors": ["https://docker.1ms.run", "https://docker.xuanyuan.me"]
}
EOF
systemctl daemon-reload
systemctl restart docker
echo "Docker mirror configured."

echo "=== Step 2: Pull images ==="
cd /data/spug-deploy
docker pull mariadb:10.8
docker pull openspug/spug-service
echo "Images pulled."

echo "=== Step 3: Start containers ==="
docker-compose up -d
echo "Containers started. Waiting 30s for DB init..."
sleep 30

echo "=== Step 4: Initialize Spug ==="
docker exec spug init_spug admin Clbr@Spug2024
echo "Spug initialized."

echo "=== Step 5: Replace with custom code ==="
docker cp /tmp/spug_api.tar.gz spug:/tmp/
docker cp /tmp/spug_web.tar.gz spug:/tmp/
docker exec spug bash -c "cd /data/spug && tar xzf /tmp/spug_api.tar.gz --strip-components=1 -C spug_api/ && tar xzf /tmp/spug_web.tar.gz --strip-components=1 -C spug_web/"
echo "Custom code deployed."

echo "=== Step 6: Install extra Python dependencies ==="
docker exec spug bash -c "pip3 install --no-cache-dir -i https://mirrors.aliyun.com/pypi/simple/ pysnmp==4.4.12 apscheduler==3.7.0"
echo "Extra dependencies installed."

echo "=== Step 7: Run netmon migrations ==="
docker exec spug bash -c "cd /data/spug/spug_api && python manage.py makemigrations netmon && python manage.py migrate netmon"
echo "Netmon migrations done."

echo "=== Step 8: Restart services ==="
docker restart spug
echo "Spug restarted. Waiting 15s..."
sleep 15

echo "=== Step 9: Verify ==="
docker ps
curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" http://localhost/
echo "=== Deployment complete! ==="