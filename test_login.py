import urllib.request
import json

data = json.dumps({'username': 'admin', 'password': 'Clbr@Spug2024', 'type': 'default'}).encode()
req = urllib.request.Request('http://127.0.0.1/api/account/login/', data=data, headers={'Content-Type': 'application/json'})
try:
    resp = urllib.request.urlopen(req)
    print('Status:', resp.status)
    print('Body:', resp.read().decode())
except urllib.error.HTTPError as e:
    print('HTTP Error:', e.code)
    print('Body:', e.read().decode())