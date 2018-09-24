#!/bin/sh

protoc --proto_path=proto --java_out=java  `find . -name '*.proto'`
