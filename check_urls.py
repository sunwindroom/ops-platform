import os
os.environ['DJANGO_SETTINGS_MODULE'] = 'spug.settings'
import django
django.setup()
from django.urls import get_resolver
r = get_resolver()
for p in r.url_patterns:
    print(p.pattern, getattr(p, 'namespace', ''))