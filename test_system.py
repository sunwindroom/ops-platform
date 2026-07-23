#!/usr/bin/env python3
"""Comprehensive system test for ops-platform on 192.168.10.203"""
import urllib.request
import json
import time
import sys

BASE = 'http://127.0.0.1/api'
TOKEN = None
PASS = 0
FAIL = 0

def log_test(name, passed, detail=''):
    global PASS, FAIL
    status = 'PASS' if passed else 'FAIL'
    if passed:
        PASS += 1
    else:
        FAIL += 1
    msg = f'[{status}] {name}'
    if detail:
        msg += f' - {detail}'
    print(msg)

def api(method, path, body=None, token=True):
    url = BASE + path
    data = json.dumps(body).encode() if body else None
    headers = {'Content-Type': 'application/json'} if body else {}
    if token and TOKEN:
        headers['x-token'] = TOKEN
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(req)
        result = json.loads(resp.read().decode())
        return resp.status, result
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read().decode())
        except:
            body = {}
        return e.code, body

# ===== 1. Infrastructure Tests =====
print('=' * 60)
print('1. Infrastructure Tests')
print('=' * 60)

# Test frontend
req = urllib.request.Request('http://127.0.0.1/')
try:
    resp = urllib.request.urlopen(req)
    fe_code = resp.status
except urllib.error.HTTPError as e:
    fe_code = e.code
log_test('Frontend accessible', fe_code == 200, f'HTTP {fe_code}')

# Test login
code, result = api('POST', '/account/login/', {'username': 'admin', 'password': 'Clbr@Spug2024', 'type': 'default'}, token=False)
if code == 200 and result.get('data', {}).get('access_token'):
    TOKEN = result['data']['access_token']
    log_test('Login', True, f'token={TOKEN[:8]}...')
else:
    log_test('Login', False, str(result))
    sys.exit(1)

# Test MySQL
import subprocess
r = subprocess.run(['mysql', '-uroot', '-pClbr@Mysql2024', '-e', 'SELECT 1'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
log_test('MySQL connection', r.returncode == 0)

# Test Redis
r = subprocess.run(['redis-cli', 'ping'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
log_test('Redis connection', b'PONG' in r.stdout)

# ===== 2. Core Spug API Tests =====
print('\n' + '=' * 60)
print('2. Core Spug API Tests')
print('=' * 60)

code, result = api('GET', '/home/')
log_test('Home API', code == 200 and 'data' in result)

code, result = api('GET', '/setting/basic/', token=False)
log_test('Setting API (public)', code == 200)

code, result = api('GET', '/host/')
log_test('Host API', code == 200)

code, result = api('GET', '/exec/')
log_test('Exec API', code == 200)

code, result = api('GET', '/alarm/')
log_test('Alarm API', code == 200)

code, result = api('GET', '/deploy/')
log_test('Deploy API', code == 200)

# ===== 3. Netmon API Tests =====
print('\n' + '=' * 60)
print('3. Netmon API Tests')
print('=' * 60)

# Overview
code, result = api('GET', '/netmon/overview/')
log_test('Netmon Overview', code == 200 and 'device_total' in result.get('data', {}), f'devices={result.get("data",{}).get("device_total","?")}')

# Groups
code, result = api('GET', '/netmon/group/')
log_test('Netmon Groups', code == 200, f'groups={len(result.get("data",[]))}')

# Create group
code, result = api('POST', '/netmon/group/', {'name': 'Test-Group', 'sort_id': 5})
log_test('Create Group', code == 200 and result.get('error') is None)

# Devices
code, result = api('GET', '/netmon/device/')
log_test('Netmon Devices', code == 200, f'devices={len(result.get("data",[]))}')

# Create device
code, result = api('POST', '/netmon/device/', {
    'name': 'Test-Router', 'ip': '192.168.10.254',
    'category': 'router', 'group_id': 1, 'monitor_type': 'ping', 'rate': 60
})
log_test('Create Device', code == 200 and result.get('error') is None)

# Topology
code, result = api('GET', '/netmon/topology/?group_id=1')
log_test('Netmon Topology', code == 200 and 'nodes' in result.get('data', {}), f'nodes={len(result.get("data",{}).get("nodes",[]))}')

# Anomalies
code, result = api('GET', '/netmon/anomaly/')
log_test('Netmon Anomalies', code == 200)

# Reports
code, result = api('GET', '/netmon/report/')
log_test('Netmon Reports', code == 200)

# Create report
code, result = api('POST', '/netmon/report/', {
    'name': 'Daily Test Report', 'report_type': 'daily', 'group_id': 1
})
log_test('Create Report', code == 200 and result.get('error') is None)

# Metric history
code, result = api('GET', '/netmon/metric/history/?device_id=1&metric_key=rtt&minutes=60')
log_test('Metric History', code == 200)

# ===== 4. Service Reliability Tests =====
print('\n' + '=' * 60)
print('4. Service Reliability Tests')
print('=' * 60)

# Check systemd services
for svc in ['spug-api', 'spug-ws', 'spug-worker', 'spug-netmon', 'nginx']:
    r = subprocess.run(['systemctl', 'is-active', svc], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    log_test(f'Service {svc}', b'active' in r.stdout, r.stdout.decode().strip())

# Check gunicorn workers
r = subprocess.run(['pgrep', '-c', '-f', 'gunicorn'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
log_test('Gunicorn workers', int(r.stdout.strip()) >= 4, f'workers={r.stdout.strip().decode()}')

# Check daphne
r = subprocess.run(['pgrep', '-c', '-f', 'daphne'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
log_test('Daphne process', int(r.stdout.strip()) >= 1)

# Check netmon scheduler
r = subprocess.run(['pgrep', '-c', '-f', 'runnetmon'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
log_test('Netmon scheduler', int(r.stdout.strip()) >= 1)

# API response time test
start = time.time()
for i in range(10):
    api('GET', '/netmon/overview/')
elapsed = time.time() - start
avg_ms = (elapsed / 10) * 1000
log_test('API response time', avg_ms < 500, f'avg={avg_ms:.0f}ms')

# ===== 5. Data Integrity Tests =====
print('\n' + '=' * 60)
print('5. Data Integrity Tests')
print('=' * 60)

# Check DB tables
r = subprocess.run(['mysql', '-uroot', '-pClbr@Mysql2024', 'spug', '-e', 'SHOW TABLES;'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
tables = [l.strip() for l in r.stdout.decode().strip().split('\n')[1:]]
log_test('DB tables exist', len(tables) >= 30, f'{len(tables)} tables')

# Check netmon tables
netmon_tables = [t for t in tables if t.startswith('netmon_')]
log_test('Netmon tables', len(netmon_tables) >= 7, f'{netmon_tables}')

# Check device count in DB
r = subprocess.run(['mysql', '-uroot', '-pClbr@Mysql2024', 'spug', '-e', 'SELECT COUNT(*) FROM netmon_devices;'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
count = r.stdout.decode().strip().split('\n')[-1].strip() if r.returncode == 0 else '0'
log_test('Device records in DB', int(count) >= 2, f'count={count}')

# ===== Summary =====
print('\n' + '=' * 60)
print(f'SUMMARY: {PASS} passed, {FAIL} failed out of {PASS + FAIL} tests')
print('=' * 60)
sys.exit(0 if FAIL == 0 else 1)