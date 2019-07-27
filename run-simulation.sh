#!/bin/bash

# NOTE: This script makes use of the exec-maven-plugin. For your
# own custom Yamcs application, you should consider using
# the yamcs-maven-plugin instead:
#
#   https://www.yamcs.org/yamcs-maven/yamcs-maven-plugin
#
# The only reason we are not using it here, is to avoid a cyclic
# dependency between both projects.

YAMCS_OPTS="--log-output $@"

mvn -q -f simulation/pom.xml exec:exec \
    -Dexec.executable="java" \
    -Dexec.args="-classpath %classpath org.yamcs.YamcsServer $YAMCS_OPTS"

