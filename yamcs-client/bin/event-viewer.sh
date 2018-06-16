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

#otherwise we get some errors when connecting to CD-MCS CIS
LANG=C
export LANG

# run the program
exec "$_RUNJAVA" org.yamcs.ui.eventviewer.EventViewer "$@"
