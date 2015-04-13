#!/bin/sh

protoc --java_out=java yamcs.proto
protoc --java_out=java pvalue.proto
protoc --java_out=java commanding.proto
protoc --java_out=java yamcs-management.proto
protoc --java_out=java comp.proto
protoc --java_out=java rest.proto
protoc --java_out=java websocket.proto


#protosuff is handled from maven with 
# mvn protostuff:compile
