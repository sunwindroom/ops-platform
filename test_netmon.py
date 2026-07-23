import urllib.request
import json

TOKEN = '5491eec1c34e47659bbc38418cb846ff'
BASE = 'http://127.0.0.1:9001'

def api_get(path):
    req = urllib.request.Request(BASE + path, headers={'x-token': TOKEN})
    try:
        resp = urllib.request.urlopen(req)
        data = json.loads(resp.read().decode())
        return data
    except urllib.error.HTTPError as e:
        return {'error': e.read().decode()}

print('=== Overview ===')
r = api_get('/netmon/overview/')
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== Groups ===')
r = api_get('/netmon/group/')
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== Devices ===')
r = api_get('/netmon/device/')
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== Anomalies ===')
r = api_get('/netmon/anomaly/')
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])

print('\n=== Reports ===')
r = api_get('/netmon/report/')
print(json.dumps(r, indent=2, ensure_ascii=False)[:500])