#!/bin/sh
#creates a runnable environment that allow to run yamcs from the command line using links to the development tree
# the configuration files and the runnable shell files from the bin directory are not overwritten

# the script has to be run each time the version number is changed, and has to run when everything is properly compiled from the command line



#set this to where you want to live "installation" to be performed
TARGET="live"


YAMCS_HOME=`pwd`

mkdir -p $TARGET/etc
mkdir -p $TARGET/bin

rm -rf $TARGET/lib
mkdir -p $TARGET/lib/ext


for f in $YAMCS_HOME/yamcs-core/etc/* ; do
    cp -an $f $TARGET/etc/
done
for f in $YAMCS_HOME/yamcs-core/bin/* ; do
    cp -an $f $TARGET/bin/
done
ln -fs $YAMCS_HOME/yamcs-core/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-core/lib/*.jar $TARGET/lib

ln -fs $YAMCS_HOME/yamcs-web/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/target/dependency/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/src/main/resources/mime.types $TARGET/etc


mkdir -p $TARGET/mdb
ln -fs $YAMCS_HOME/yamcs-core/mdb/* $TARGET/mdb


