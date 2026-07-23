import os, django
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'spug.settings')
django.setup()
from django.http import HttpResponse
from django.test import RequestFactory

def debug_headers(request):
    headers = {k: v for k, v in request.META.items() if k.startswith('HTTP_')}
    return HttpResponse(str(sorted(headers.items())), content_type='text/plain')

from django.urls import path
from django.conf.urls import url
urlpatterns = [path('debug/', debug_headers)]