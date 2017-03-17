#!/bin/bash
set -e

function start_server {

  # Allow extending containers to hook into
  # this entrypoint, enabling runtime addition
  # of plugin jars, configuration settings, etc
  echo
  for f in /entrypoint.d/*; do
    case "$f" in
      *.sh)     echo "$0: running $f"; . "$f" ;;
    esac
    echo
  done

  # Relative paths are not yet well supported, so cd
  # into dir to prevent problems
  cd /opt/yamcs
  ./bin/yamcs-server.sh
}

if [ "$1" == 'yamcs' ]; then
  # Currently this will crash if extending containers
  # Don't apply proper configuration. Should maybe do
  # something about it, but Yamcs has no purpose
  # without being configured for a mission.
  start_server
elif [ "$1" == 'yss' ]; then
  # Prepares a demo configuration called YSS
  # short for Yamcs Simulation System
  # Useful as a quick demo, or for core development
  rm -rf /opt/yamcs/{etc,mdb}
  mkdir /opt/yamcs/{etc,mdb}

  cp /src/yamcs/yamcs-simulation/etc/* /opt/yamcs/etc
  cp /src/yamcs/yamcs-simulation/bin/* /opt/yamcs/bin

  ln -fs /src/yamcs/yamcs-simulation/target/*.jar /opt/yamcs/lib
  ln -fs /src/yamcs/yamcs-simulation/mdb/* /opt/yamcs/mdb
  ln -fs /src/yamcs/yamcs-simulation/web /opt/yamcs/web/yss

  cp /src/yamcs/yamcs-simulation/bin/simulator.sh /opt/yamcs/bin
  
  YAMCS_DATA=/storage/yamcs-data/
  mkdir -p $YAMCS_DATA/simulator/profiles
  cp /src/yamcs/yamcs-simulation/profiles/* $YAMCS_DATA/simulator/profiles

  ln -s /src/yamcs/yamcs-core/mdb/* /opt/yamcs/mdb
  for f in /src/yamcs/yamcs-core/etc/* ; do
    case "$f" in
      *.sample)
        FILENAME=$(basename "$f")
        cp -an "$f" /opt/yamcs/etc/${FILENAME%.*}
        ;;
      *)
        cp -an "$f" /opt/yamcs/etc/
        ;;
    esac
  done

  if [ "$2" == 'leo_spacecraft' ]; then
    sed -i 's/landing/leo_spacecraft/g' /opt/yamcs/etc/yamcs.simulator.yaml
  fi

  # TODO get this out of here, and back into compose
  sed -i 's/localhost/simulator/g' /opt/yamcs/etc/tcp.yaml
  sed -i 's#web/base#web/base_2/build#g' /opt/yamcs/etc/yamcs.yaml

  start_server
else
  exec "$@"
fi
