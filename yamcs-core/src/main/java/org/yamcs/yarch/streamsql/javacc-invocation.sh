#!/bin/sh

javacc -nostatic -JDK_VERSION=1.6 StreamSql.jj
rm ParseException.java SimpleCharStream.java Token.java 
