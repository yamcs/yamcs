#!/bin/bash

YAMCS_OPTS="$YAMCS_OPTS ${@:2}"

print_example_list () {
    for dir in `find examples -maxdepth 1 -mindepth 1 -type d ! -name '.*' ! -name snippets -exec basename {} \; | sort`; do
        echo "    $dir"
    done
}

if [[ -z "$1" ]]; then
    echo "usage: $0 EXAMPLE [options]"
    echo
    echo "Where EXAMPLE is one of:"
    print_example_list
    exit 1
fi
if [[ ! -d "examples/$1" ]]; then
    echo "Cannot find an example by the name '$1'. Use one of:"
    print_example_list
    exit 1
fi

mvn -f "examples/$1/pom.xml" yamcs:run \
    -Dyamcs.args="$YAMCS_OPTS"
