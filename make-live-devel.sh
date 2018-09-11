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

rm -rf $TARGET/lib
mkdir -p $TARGET/lib/ext

cp -an $YAMCS_HOME/yamcs-server/bin/* $TARGET/bin
cp -an $YAMCS_HOME/yamcs-client/bin/* $TARGET/bin

ln -fs $YAMCS_HOME/yamcs-core/mdb/* $TARGET/mdb

ln -fs $YAMCS_HOME/yamcs-core/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-artemis/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-artemis/lib/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-server/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-server/lib/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/packages/app/dist $TARGET/lib/yamcs-web
ln -fs $YAMCS_HOME/yamcs-client/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-client/lib/jcommander*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-client/lib/jdatepicker*.jar $TARGET/lib

# Sets up a development environment for an example Yamcs configuration
if [ $YSS_CONFIGURATION -eq "1" ]; then
    YAMCS_DATA=/storage/yamcs-data

    cp -an $YAMCS_HOME/yamcs-simulation/bin/* $TARGET/bin

    ln -fs $YAMCS_HOME/yamcs-simulation/target/*.jar $TARGET/lib
    cp -an $YAMCS_HOME/yamcs-simulation/etc/* $TARGET/etc
    ln -fs $YAMCS_HOME/yamcs-simulation/mdb/* $TARGET/mdb
    ln -fs $YAMCS_HOME/yamcs-simulation/test_data $TARGET/
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
