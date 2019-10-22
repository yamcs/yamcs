#!/bin/bash

YAMCS_OPTS="$YAMCS_OPTS $@"

mvn -f simulation/pom.xml yamcs:run \
    -Dyamcs.args="$YAMCS_OPTS"

