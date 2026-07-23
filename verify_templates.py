import urllib.request, json
TOKEN = '5491eec1c34e47659bbc38418cb846ff'
req = urllib.request.Request('http://127.0.0.1/api/exec/template/', headers={'x-token': TOKEN})
resp = urllib.request.urlopen(req)
data = json.loads(resp.read().decode())
print('模板分类:', data['data']['types'])
print('模板总数:', len(data['data']['templates']))
for t in data['data']['templates']:
    print('  -', t['name'], '[' + t['type'] + ']')