#!/bin/bash

YAMCSADMIN_OPTS="$YAMCSADMIN_OPTS $@"

mvn -q -f simulation/pom.xml exec:exec \
    -Dexec.executable="java" \
    -Dexec.args="-classpath %classpath org.yamcs.cli.YamcsAdminCli $YAMCSADMIN_OPTS"

