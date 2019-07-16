#!/bin/bash

# NOTE: This script makes use of the exec-maven-plugin. For your
# own custom Yamcs application, you should consider using
# the yamcs-maven-plugin instead:
#
#   https://www.yamcs.org/yamcs-maven/yamcs-maven-plugin
#
# The only reason we are not using it here, is to avoid a cyclic
# dependency between both projects.

JAVA_OPTS="-Djava.util.logging.config.file=etc/logging.properties"
mvn -f yamcs-simulation/pom.xml exec:exec \
    --quiet \
    -Dexec.executable="java" \
    -Dexec.args="-classpath %classpath:etc $JAVA_OPTS org.yamcs.YamcsServer"

