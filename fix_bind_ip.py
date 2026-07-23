import os
os.environ['DJANGO_SETTINGS_MODULE'] = 'spug.settings'
os.environ['MYSQL_DATABASE'] = 'spug'
os.environ['MYSQL_USER'] = 'root'
os.environ['MYSQL_PASSWORD'] = 'Clbr@Mysql2024'
os.environ['MYSQL_HOST'] = '127.0.0.1'
os.environ['MYSQL_PORT'] = '3306'
import django
django.setup()
from apps.setting.models import Setting
s, created = Setting.objects.get_or_create(key='bind_ip', defaults={'value': 'false'})
if not created:
    s.value = 'false'
    s.save()
print('bind_ip set to false')