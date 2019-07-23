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

# Add all necessary jars
CLASSPATH="$YAMCS_HOME/lib/*:$YAMCS_HOME/lib/ext/*"
# Add etc directory to load config resources
CLASSPATH=$YAMCS_HOME/etc:$CLASSPATH:$YAMCS_HOME

export CLASSPATH

if [ -d "$JAVA_HOME" ]; then
  _RUNJAVA="$JAVA_HOME/bin/java"
else
  _RUNJAVA=java
fi

exec "$_RUNJAVA" -Xmx128m org.yamcs.ui.packetviewer.PacketViewer "$@"
