#!/usr/bin/env python
from urllib import urlretrieve, URLopener
from io import open
import shutil
from time import gmtime, strftime

 
catalinaBase = '${catalinaBase}'

# BACKUP catalina.properties
src = catalinaBase + '/conf/catalina.properties'
dst = catalinaBase + '/conf/catalina-' + strftime('%Y%m%d-%H%M%S', gmtime()) + '.properties'
shutil.copy(src, dst)

print('Created backup ' + dst)

f = open(src, 'ab')

f.write('\n\n')
f.write('# BEGIN OF ADDED BY CLOUD-INIT ' + strftime('%Y/%m/%d-%H:%M:%S', gmtime()) + '#\n')
f.write('\n')

<#list systemProperties?keys as property>
f.write('${property}=${systemProperties[property]}\n')
</#list>

f.write('\n')
f.write('jdbc.driverClassName=com.mysql.jdbc.Driver\n')
f.write('\n')
f.write('# Properties that control the population of schema and data for a new data source\n')
f.write('jdbc.initLocation=classpath:db/mysql/initDB.txt\n')
f.write('jdbc.dataLocation=classpath:db/mysql/populateDB.txt\n')
f.write('\n')
f.write('# Property that determines which Hibernate dialect to use\n')
f.write('# (only applied with "applicationContext-hibernate.xml")\n')
f.write('hibernate.dialect=org.hibernate.dialect.MySQLDialect\n')
f.write('\n')
f.write('# Property that determines which database to use with an AbstractJpaVendorAdapter\n')
f.write('jpa.database=MYSQL')

f.write('\n')
f.write('# END OF ADDED BY CLOUD-INIT #\n')
f.write('\n')
f.close()

print('Updated ' + src)

# DOWNLOAD WAR
proxies = {}
url = '${warUrl}'
temporaryfilename = '/tmp/${warName}'

URLopener(proxies).retrieve(url, temporaryfilename)
print('Downloaded ' + temporaryfilename)

# DEPLOY WAR
filename = catalinaBase + '/webapps/${warName}'
shutil.move(temporaryfilename, filename)
print('Deployed ' + filename)
