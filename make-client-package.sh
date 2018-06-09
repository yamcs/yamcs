#!/bin/sh

cd `dirname $0`
yamcshome=`pwd`

unset GREP_OPTIONS
# Get the latest yamcs-client/yamcs*.jar but exclude the sources jar because the client needs the compiled Java class files
yjar=`ls -t -1 yamcs-client/target/yamcs*.jar | grep -v -- -sources.jar | head -n 1`
if [ -z "$yjar" ]; then
	echo "ERROR: yamcs jar not found in yamcs-client/target/yamcs*.jar - did you build the project first? Try: mvn clean install"
	exit 1
fi

dist=yamcs-client-`echo $yjar | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+'`
version=`echo $yjar | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+'`
if [ -z "$version" ]; then
	echo "ERROR: could not extract version number from filename $yjar so we can't proceed"
	exit 1
fi

rm -rf /tmp/$dist
mkdir -p /tmp/$dist/lib
mkdir -p /tmp/$dist/etc
mkdir -p /tmp/$dist/etc/orekit
mkdir -p /tmp/$dist/bin

cd /tmp/$dist

ln -s $yamcshome/$yjar lib/
ln -s $yamcshome/yamcs-client/lib/*.jar lib/
ln -s $yamcshome/yamcs-client/bin/* bin/
cp $yamcshome/yamcs-core/etc/UTC-TAI.history etc/orekit
cp $yamcshome/yamcs-client/etc/yamcs-ui.yaml.sample etc/yamcs-ui.yaml
cp $yamcshome/yamcs-client/etc/event-viewer.yaml.sample etc/event-viewer.yaml

cd /tmp

echo "creating tarball"
tar czfh $yamcshome/$dist.tar.gz $dist
echo "creating zipfile"
zip -r $yamcshome/$dist.zip $dist

echo "cleanup"
rm -r $dist
cd $yamcshome
ls -l $dist*
