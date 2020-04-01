#!/bin/bash

YAMCSADMIN_OPTS="$YAMCSADMIN_OPTS $@"

mvn -q -f simulation/pom.xml yamcs:run-tool \
    -Dyamcs.tool="org.yamcs.cli.YamcsAdminCli" \
    -Dyamcs.args="$YAMCSADMIN_OPTS"

