import urllib.request
import json

TOKEN = '5491eec1c34e47659bbc38418cb846ff'
BASE = 'http://127.0.0.1:9001'

def api_post(path, body):
    data = json.dumps(body).encode()
    req = urllib.request.Request(BASE + path, data=data, headers={'x-token': TOKEN, 'Content-Type': 'application/json'})
    try:
        resp = urllib.request.urlopen(req)
        return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return {'error': e.read().decode()}

def api_get(path):
    req = urllib.request.Request(BASE + path, headers={'x-token': TOKEN})
    try:
        resp = urllib.request.urlopen(req)
        return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return {'error': e.read().decode()}

print('=== Create Group ===')
r = api_post('/netmon/group/', {'name': 'Core-Network', 'sort_id': 10})
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== Create Device ===')
r = api_post('/netmon/device/', {
    'name': 'Core-Switch-01',
    'ip': '192.168.10.1',
    'category': 'switch',
    'group_id': 1,
    'monitor_type': 'ping',
    'rate': 60
})
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== Create Device 2 ===')
r = api_post('/netmon/device/', {
    'name': 'App-Server-01',
    'ip': '192.168.10.100',
    'category': 'server',
    'group_id': 1,
    'monitor_type': 'ping',
    'rate': 60
})
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== List Devices ===')
r = api_get('/netmon/device/')
print(json.dumps(r, indent=2, ensure_ascii=False)[:800])

print('\n=== Overview ===')
r = api_get('/netmon/overview/')
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== Topology ===')
r = api_get('/netmon/topology/?group_id=1')
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])