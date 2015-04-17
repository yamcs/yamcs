#!/bin/sh

cd `dirname $0`
yamcshome=`pwd`

unset GREP_OPTIONS
yjar=`ls -t -1 yamcs-core/target/yamcs*.jar | head -n 1`
dist=yamcs-client-`echo $yjar | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+'`
version=`echo $yjar | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+'`


rm -rf /tmp/$dist
mkdir -p /tmp/$dist/lib
mkdir -p /tmp/$dist/etc
mkdir -p /tmp/$dist/etc/orekit
mkdir -p /tmp/$dist/bin

cd /tmp/$dist
ln -s $yamcshome/yamcs-core/lib/*.jar lib/
ln -s $yamcshome/$yjar lib/
ln -s $yamcshome/yamcs-core/bin/event-viewer.* bin/
ln -s $yamcshome/yamcs-core/bin/yamcs-monitor.* bin/
ln -s $yamcshome/yamcs-core/bin/packet-viewer.* bin/
ln -s $yamcshome/yamcs-core/bin/setclasspath.sh bin/
ln -s $yamcshome/yamcs-core/bin/lcp.bat bin/
cp $yamcshome/yamcs-core/etc/UTC-TAI.history etc/orekit
cp $yamcshome/yamcs-core/etc/yamcs-ui.yaml.sample etc/yamcs-ui.yaml
cp $yamcshome/yamcs-core/etc/event-viewer.yaml.sample etc/event-viewer.yaml

cd /tmp

echo "creating tarball"
echo tar czfh $yamcshome/$dist.tar.gz $dist
tar czfh $yamcshome/$dist.tar.gz $dist
zip -r $yamcshome/$dist.zip $dist
rm -r $dist

echo "cleanup"
cd $yamcshome
ls -l $dist*
