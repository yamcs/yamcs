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

# run the program
exec "$_RUNJAVA" $JAVA_LIBRARY_PATH -Djava.util.logging.config.file=$YAMCS_HOME/etc/logging.yarch.properties org.yamcs.yarch.HistogramDb "$@"
