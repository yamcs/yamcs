#!/bin/bash

export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd yamcs-web/src/main/webapp && npm run build && cd - && ./run-example.sh simulation