#!/bin/sh

# Variables
# ---------
# DO NOT MODIFY THIS FILE
# Instead set variables via a script YAMCS_HOME/bin/setenv.sh
#
# JMX           Set to 1 to allow remote JMX connections (jconsole).
#               (only temporarily for debugging purposes !)
#
# JAVA_OPTS     Java runtime options

# resolve links - $0 may be a softlink
PRG="$0"

while [ -h "$PRG" ]; do
  ls=`ls -ld "$PRG"`
  link=`expr "$ls" : '.*-> \(.*\)$'`
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=`dirname "$PRG"`/"$link"
  fi
done

# Get standard environment variables
PRGDIR=`dirname "$PRG"`
YAMCS_HOME=`cd "$PRGDIR/.." ; pwd`

if [ -r "$YAMCS_HOME/bin/setenv.sh" ]; then
  . "$YAMCS_HOME/bin/setenv.sh"
fi

# set classpath
. "$YAMCS_HOME"/bin/setclasspath.sh

if [ "$JMX" = 1 ]; then
  JMX_OPTS="-Dcom.sun.management.jmxremote.port=9999  -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false" 
fi

# run the program
exec "$_RUNJAVA" $MODULE_OPTS $JAVA_OPTS $JMX_OPTS\
    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/\
    -Djxl.nowarnings=true \
    -Djava.util.logging.config.file=$YAMCS_HOME/etc/logging.yamcs-server.properties \
    -Djacorb.home=$YAMCS_HOME\
    -Djavax.net.ssl.trustStore=$YAMCS_HOME/etc/trustStore\
    -Dapple.awt.UIElement=true\
    org.yamcs.YamcsServer "$@"
