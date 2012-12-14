#!/bin/sh

cd `dirname $0`
yamcshome=`pwd`

unset GREP_OPTIONS
yjar=`ls target/yamcs*.jar -t -1 | head -n 1`
dist=yamcs-client-`echo $yjar | grep -Eo '[0-9]+\.[0-9]+\.[0-9]+'`

rm -rf /tmp/$dist
mkdir -p /tmp/$dist/lib
mkdir -p /tmp/$dist/etc
mkdir -p /tmp/$dist/bin
cd /tmp/$dist
ln -s $yamcshome/lib/jacorb* lib/
ln -s $yamcshome/lib/systemControl* lib/
ln -s $yamcshome/lib/slf4j* lib/
ln -s $yamcshome/$yjar lib/
cp $yamcshome/etc/jacorb.properties etc/
ln -s $yamcshome/etc/TriboLAB_OnEventMsgsCalibration.csv etc/
ln -s $yamcshome/etc/TriboLAB_OnEventMessages.csv etc/
ln -s $yamcshome/bin/cis-raw-packet-dump.* bin/
ln -s $yamcshome/bin/cis-parameter-dump.* bin/
ln -s $yamcshome/bin/event-viewer.* bin/
ln -s $yamcshome/bin/yamcs-monitor.* bin/
ln -s $yamcshome/bin/archive-browser.* bin/
ln -s $yamcshome/bin/egsecisif.* bin/
ln -s $yamcshome/bin/lcp.bat bin/

cat etc/jacorb.properties | 
sed -e 's/jacorb.security.support_ssl=.*$/jacorb.security.support_ssl=on/g' |
sed -e 's/^org.omg.PortableInterceptor.ORBInitializerClass.GSSUPProvider=.*$/#&/g' |
sed -e 's/^org.omg.PortableInterceptor.ORBInitializerClass.SAS=.*$/#&/g' >jacorb.properties.tmp;
mv jacorb.properties.tmp etc/jacorb.properties

cd /tmp
excl=find-$$
find $dist -follow -path '*.svn*' >$excl
echo "excluding `wc -l <$excl` files"

echo "creating tarball"
echo tar czfh $yamcshome/$dist.tar.gz -X $excl $dist
tar czfh $yamcshome/$dist.tar.gz -X $excl $dist
zip -r $yamcshome/$dist.zip -x@$excl $dist
rm -r $dist

echo "cleanup"
rm -rf /tmp/$excl
cd $yamcshome
ls -l $dist*
