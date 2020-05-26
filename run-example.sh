#!/bin/bash

YAMCS_OPTS="$YAMCS_OPTS ${@:2}"

if [[ -z "$1" ]]; then
    echo "usage: $0 EXAMPLE [options]"
    echo
    echo "Where EXAMPLE is one of:"
    for dir in `find examples -type d -depth 1 -exec basename {} \; | sort`; do
        echo "    $dir"
    done
    exit 1
fi
if [[ ! -d "examples/$1" ]]; then
    echo "Cannot find an example by the name '$1'. Use one of:"
    for dir in `find examples -type d -depth 1 -exec basename {} \; | sort`; do
        echo "    $dir"
    done
    exit 1
fi

mvn -f "examples/$1/pom.xml" yamcs:run \
    -Dyamcs.args="$YAMCS_OPTS"
