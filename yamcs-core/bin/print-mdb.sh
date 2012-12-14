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


# run the program
if [ $# -ne 2 ] ; then 
  echo "Usage: $0 [XTCE|PP] config"
  exit 1
fi

case "$1" in
   XTCE) 
     "$_RUNJAVA" -classpath "$CLASSPATH" org.yamcs.xtceproc.XtceDbFactory "$2"
      ;;
   PP)
     "$_RUNJAVA" -classpath "$CLASSPATH" org.yamcs.ppdb.PpDbFactory "$2"
     ;;
   *)
     echo "Usage: $0 [XTCE|PP] config"
     exit 1
esac
