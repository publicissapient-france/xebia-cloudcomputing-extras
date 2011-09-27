JAVA_HOME=/usr/java/jdk1.6.0_27
export JAVA_HOME

PUBLIC_IP_ADDRESS=`curl http://169.254.169.254/latest/meta-data/public-ipv4`
export PUBLIC_IP_ADDRESS

CATALINA_OPTS_JMX=" \
   -Djava.rmi.server.hostname=$PUBLIC_IP_ADDRESS \
   -Dcom.sun.management.jmxremote \
   -Dcom.sun.management.jmxremote.port=6969 \
   -Dcom.sun.management.jmxremote.ssl=false \
   -Dcom.sun.management.jmxremote.authenticate=false"

CATALINA_OPTS_JVM=" \
        -XX:MaxPermSize=128m -Xmx350m \
        -XX:+HeapDumpOnOutOfMemoryError \
        -XX:HeapDumpPath=$CATALINA_BASE/logs/ \
        -Djava.awt.headless=true"

CATALINA_OPTS="
   -server $CATALINA_OPTS \
   $CATALINA_OPTS_JMX \
   $CATALINA_OPTS_JVM"

export CATALINA_OPTS
