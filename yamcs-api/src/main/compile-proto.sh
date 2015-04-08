#!/bin/sh

protoc --java_out=java yamcs.proto
protoc --java_out=java pvalue.proto
protoc --java_out=java commanding.proto
protoc --java_out=java yamcs-management.proto
protoc --java_out=java comp.proto
protoc --java_out=java rest.proto
protoc --java_out=java websocket.proto


java -jar /tmp/protostuff-compiler-1.0.7-jarjar.jar modules.properties 
