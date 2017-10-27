#!/bin/sh

if [ -z "$YAMCS_HOME" ]; then
  echo "The YAMCS_HOME variable must be set before running this script"
  exit 1
fi

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
