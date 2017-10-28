#!/bin/sh
# Sets up a development environment for an example Yamcs configuration.

YAMCS_DATA=/storage/yamcs-data/

. "$PWD/make-live-devel.sh" example

ln -fs $YAMCS_HOME/yamcs-simulation/etc/* $TARGET/etc
ln -fs $YAMCS_HOME/yamcs-simulation/bin/* $TARGET/bin

ln -fs $YAMCS_HOME/yamcs-simulation/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-simulation/mdb/* $TARGET/mdb

rm -f $TARGET/web/yss
ln -fs $YAMCS_HOME/yamcs-simulation/web $TARGET/web/yss

ln -fs $YAMCS_HOME/yamcs-simulation/bin/simulator.sh $TARGET/bin
ln -fs $YAMCS_HOME/yamcs-simulation/test_data $TARGET/

ln -fs $YAMCS_HOME/yamcs-simulation/profiles $YAMCS_DATA/simulator
if [ $? -ne 0 ]; then
    echo "ERROR: could not create $YAMCS_DATA/simulator/profiles - please create it and make sure this script has write permissions in it!"
    exit 1
fi
if [ ! -w "$YAMCS_DATA/simulator/profiles" ]; then
    echo "ERROR: please make sure this script has write permissions in the $YAMCS_DATA/simulator/profiles folder!"
    exit 2
fi

echo "Example installed to `ls -d -1 $PWD/example`"
