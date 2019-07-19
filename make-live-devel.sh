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

mkdir -p $TARGET/bin
mkdir -p $TARGET/etc
mkdir -p $TARGET/log
mkdir -p $TARGET/mdb

rm -rf $TARGET/lib
mkdir -p $TARGET/lib/ext

cp -an $YAMCS_HOME/yamcs-server/bin/* $TARGET/bin

ln -fs $YAMCS_HOME/yamcs-core/mdb/* $TARGET/mdb

ln -fs $YAMCS_HOME/yamcs-core/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-artemis/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-artemis/target/dependency/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-tse/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-tse/target/dependency/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-server/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-server/target/dependency/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/target/*.jar $TARGET/lib
ln -fs $YAMCS_HOME/yamcs-web/packages/app/dist $TARGET/lib/yamcs-web

# Sets up a development environment for an example Yamcs configuration
if [ $YSS_CONFIGURATION -eq "1" ]; then
    echo "-----------------------------------------------------"
    echo "Yamcs is moving away from the 'live' dev config."
    echo "The new way to run the simulation is:"
    echo "   ./run-simulation.sh"
    echo "-----------------------------------------------------"
    ln -fs $YAMCS_HOME/yamcs-simulation/target/*.jar $TARGET/lib
    cp -an $YAMCS_HOME/yamcs-simulation/etc/* $TARGET/etc
    ln -fs $YAMCS_HOME/yamcs-simulation/mdb/* $TARGET/mdb
else
    echo "-----------------------------------------------------"
    echo "Yamcs is moving away from the 'live' dev config."
    echo "Consider migrating to the yamcs-maven-plugin and"
    echo "let us know if you run into issues."
    echo
    echo "https://www.yamcs.org/yamcs-maven/yamcs-maven-plugin"
    echo
    echo "If you're looking to run the simulation run:"
    echo "   ./run-simulation.sh"
    echo "-----------------------------------------------------"
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


