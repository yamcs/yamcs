#!/bin/sh

protoc -I .:../../../yamcs-api/src/main/ --java_out=java comp.proto
protoc -I .:../../../yamcs-api/src/main/ --java_out=java rest.proto
protoc -I .:../../../yamcs-api/src/main/ --java_out=java websocket.proto

#workaround the inability of protostuff to include other directories in import
rm -f yamcs.proto commanding.proto pvalue.proto
ln -s ../../../yamcs-api/src/main/yamcs.proto .
ln -s ../../../yamcs-api/src/main/commanding.proto .
ln -s ../../../yamcs-api/src/main/pvalue.proto .

java -jar /tmp/protostuff-compiler-1.0.7-jarjar.jar modules.properties 
