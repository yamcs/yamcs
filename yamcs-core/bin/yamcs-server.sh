#!/bin/sh

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

# set classpath
. "$YAMCS_HOME"/bin/setclasspath.sh

#set library path (for tokyocabinet)
. "$YAMCS_HOME"/bin/setlibrarypath.sh

#To allow remote JMX (jconsole) connections, attach this to the java command
#Only temporarily for debugging!!!
JMX_REMOTE="-Dcom.sun.management.jmxremote.port=9999  -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false"


# run the program
exec "$_RUNJAVA" $JAVA_LIBRARY_PATH\
    -Xmx512m\
    -Djxl.nowarnings=true \
    -Djava.util.logging.config.file=$YAMCS_HOME/etc/logging.yamcs-server.properties \
    -Djacorb.home=$YAMCS_HOME\
    -Djavax.net.ssl.trustStore=$YAMCS_HOME/etc/trustStore\
    -Dapple.awt.UIElement=true\
	org.yamcs.YamcsServer "$@"
