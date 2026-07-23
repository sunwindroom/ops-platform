import sys
c = open('/etc/my.cnf', 'r').read()
c = c.replace('[mysqld]', '[mysqld]\nskip-grant-tables')
open('/etc/my.cnf', 'w').write(c)
print('done')