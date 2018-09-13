#!/bin/sh

protoc --java_out=java tablespace.proto
protoc --proto_path=../../../yamcs-api/src/main --proto_path=. --java_out=java cmdhistory.proto
