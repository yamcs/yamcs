#!/bin/sh
#creates a runnable environment that allow to run yamcs from the command line using links to the development tree
# the configuration files and the runnable shell files from the bin directory are not overwritten

# the script has to be run each time the version number is changed, and has to run when everything is properly compiled from the command line



#set this to where you want the live "installation" to be performed
TARGET="live"
#Allow overriding default from the command line
if [ -n "$1" ] ; then
    TARGET="$1"
fi


YAMCS_HOME=`pwd`

mkdir -p $TARGET/etc
mkdir -p $TARGET/bin
mkdir -p $TARGET/mdb
mkdir -p $TARGET/web

rm -rf $TARGET/lib
mkdir -p $TARGET/lib/ext

cp -an $YAMCS_HOME/yamcs-core/bin/* $TARGET/bin

ln -fs $YAMCS_HOME/yamcs-core/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-core/lib/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-core/mdb/* $TARGET/mdb
ln -fs $YAMCS_HOME/yamcs-web/build $TARGET/web/base

if [ -f make-live-devel-local.sh ] ; then
    sh make-live-devel-local.sh $TARGET
else
    # Assume YSS simulator deployment
    cp -an $YAMCS_HOME/yamcs-simulation/etc/* $TARGET/etc
    cp -an $YAMCS_HOME/yamcs-simulation/bin/* $TARGET/bin

    ln -fs $YAMCS_HOME/yamcs-simulation/target/*.jar $TARGET/lib
    ln -fs $YAMCS_HOME/yamcs-simulation/mdb/* $TARGET/mdb
    ln -fs $YAMCS_HOME/yamcs-simulation/web $TARGET/web/yss

    cp -an $YAMCS_HOME/yamcs-simulation/bin/simulator.sh $TARGET/bin
    ln -fs $YAMCS_HOME/yamcs-simulation/test_data $TARGET/
    YAMCS_DATA=/storage/yamcs-data/
    # create the yamcs_data directory and add the simulator profiles
    mkdir -p $YAMCS_DATA/simulator/profiles
    if [ $? -ne 0 ]; then
        echo "ERROR: could not create $YAMCS_DATA/simulator/profiles - please create it and make sure this script has write permissions in it!"
        exit 1
    fi
    if [ ! -w "$YAMCS_DATA/simulator/profiles" ]; then
        echo "ERROR: please make sure this script has write permissions in the $YAMCS_DATA/simulator/profiles folder!"
        exit 2
    fi
    cp -an $YAMCS_HOME/yamcs-simulation/profiles/* $YAMCS_DATA/simulator/profiles
fi

# Add sample config (if not already present)
for f in $YAMCS_HOME/yamcs-core/etc/* ; do
    case "$f" in
        *.sample)
            FILENAME=$(basename "$f")
            cp -an "$f" $TARGET/etc/${FILENAME%.*}
            ;;
        *)
            cp -an "$f" $TARGET/etc/
            ;;
    esac
done
