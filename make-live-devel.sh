#!/bin/sh
#creates a runnable environment that allow to run yamcs from the command line using links to the development tree
# the configuration files and the runnable shell files from the bin directory are not overwritten

# the script has to be run each time the version number is changed, and has to run when everything is properly compiled from the command line

# Where the live environment is installed
TARGET="live"

# Whether to install the YSS example configuration
YSS_CONFIGURATION=0

usage() {
    echo "usage: $0 [-h | --help] [--yss] [directory]"
}

for arg in "$@"; do
    case "$arg" in
        "-h" | "--help")
            usage
            exit 0
            ;;
        "--yss")
            YSS_CONFIGURATION=1
            ;;
        "-"*)
            echo "Unknown option: $arg"
            usage
            exit 1
            ;;
        *)
            TARGET="$arg"
            ;;
    esac
done

PRG_DIR=`dirname $0`
YAMCS_HOME=`cd "$PRG_DIR"; pwd`

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
ln -fs $YAMCS_HOME/yamcs-artemis/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-artemis/lib/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/packages/app/dist $TARGET/lib/yamcs-web

# Sets up a development environment for an example Yamcs configuration
if [ $YSS_CONFIGURATION -eq "1" ]; then
    YAMCS_DATA=/storage/yamcs-data

    cp -an $YAMCS_HOME/yamcs-simulation/bin/* $TARGET/bin

    ln -fs $YAMCS_HOME/yamcs-simulation/target/*.jar $TARGET/lib
    cp -an $YAMCS_HOME/yamcs-simulation/etc/* $TARGET/etc
    ln -fs $YAMCS_HOME/yamcs-simulation/mdb/* $TARGET/mdb
    ln -fs $YAMCS_HOME/yamcs-simulation/test_data $TARGET/

    rm -rf $TARGET/web
    ln -fs $YAMCS_HOME/yamcs-simulation/web $TARGET/web

    mkdir -p $YAMCS_DATA/simulator/profiles
    if [ $? -ne 0 ]; then
        echo "ERROR: could not create $YAMCS_DATA/simulator/profiles - please create it and make sure this script has write permissions in it!"
        exit 1
    fi
    if [ ! -w "$YAMCS_DATA/simulator/profiles" ]; then
        echo "ERROR: please make sure this script has write permissions in the $YAMCS_DATA/simulator/profiles folder!"
        exit 2
    fi
    cp -an $YAMCS_HOME/yamcs-simulation/profiles/* $YAMCS_DATA/simulator/
fi

if [ -f make-live-devel-local.sh ]; then
    sh make-live-devel-local.sh $TARGET
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

echo "Development environment installed to `cd $TARGET; pwd`"
