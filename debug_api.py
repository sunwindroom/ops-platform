import urllib.request, json
TOKEN = '5491eec1c34e47659bbc38418cb846ff'
for path in ['/home/', '/setting/basic/', '/exec/', '/alarm/', '/deploy/']:
    req = urllib.request.Request('http://127.0.0.1/api' + path, headers={'x-token': TOKEN})
    try:
        resp = urllib.request.urlopen(req)
        print(f'{path}: {resp.status} - OK')
    except urllib.error.HTTPError as e:
        body = e.read().decode()[:100]
        print(f'{path}: {e.code} - {body}')