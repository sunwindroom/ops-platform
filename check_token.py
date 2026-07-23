import os, django, time
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'spug.settings')
django.setup()
from apps.account.models import User
u = User.objects.first()
print('token:', u.access_token)
print('expired:', u.token_expired)
print('now:', time.time())
print('is_active:', u.is_active)