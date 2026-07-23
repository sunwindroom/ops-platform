import sys
c = open('/etc/my.cnf', 'r').read()
c = c.replace('skip-grant-tables\n', '')
open('/etc/my.cnf', 'w').write(c)
print('done')