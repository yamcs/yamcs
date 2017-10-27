#!/bin/sh

# -----------------------------------------------------------------------------
#  Set CLASSPATH and Java options
# -----------------------------------------------------------------------------

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
# Add the lib directory to load the xtce derived values
CLASSPATH=$CLASSPATH:$YAMCS_HOME/lib

export CLASSPATH

# Set standard command for invoking Java.
_RUNJAVA=java
for j in /cdmcs/spaceapps/jre/bin/java ; do
	if [ -x $j ]; then _RUNJAVA=$j; break; fi
done
