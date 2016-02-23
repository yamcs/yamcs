#!/bin/bash
buildweb=1

if [ "$1" = "-noweb" ] ; then
   buildweb=0
fi


cd `dirname $0`
yamcshome=`pwd`
version=`grep -m 1 '<version>.*</version>' pom.xml | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/'`

rev=`git rev-parse --short HEAD`

dist=yamcs-${version}+r$rev

rm -rf /tmp/$dist
mkdir /tmp/$dist
 
git clone . /tmp/$dist
cd /tmp/$dist

# fix revision in pom.xml
for f in pom.xml yamcs-core/pom.xml yamcs-api/pom.xml yamcs-xtce/pom.xml yamcs-simulation/pom.xml; do
    cat $f | sed -e 's/'$version'/'$version'-'$rev/ | sed -e 's/-\${buildNumber}/'/ >$f.fixed
    mv $f.fixed $f
done

# fix the default location of the server logs
logproperties=yamcs-core/etc/logging.yamcs-server.properties.sample
sed -e 's/%h\/.yamcs\/log/\/opt\/yamcs\/log/g' $logproperties > $logproperties.tmp;
mv $logproperties.tmp $logproperties


if [ "$buildweb" = "1" ]; then
   # Bower and npm use local-scoped dependencies.
   # Setup links before packing, so deps don't get re-downloaded everytime
   ln -s "$yamcshome/yamcs-web/node_modules" yamcs-web
   ln -s "$yamcshome/yamcs-web/bower_components" yamcs-web
   if [ ! -d yamcs-web/node_modules ]; then
       echo "[WARNING] No cached npm dependencies. They will be downloaded from the internet."
   fi
   if [ ! -d yamcs-web/bower_components ]; then
       echo "[WARNING] No cached bower dependencies. They will be downloaded from the internet."
   fi
fi

cd /tmp

mkdir -p $HOME/rpmbuild/{SRPMS,RPMS,BUILD,SPECS,SOURCES,tmp}

echo "Packing up sources..."
tar czfh $HOME/rpmbuild/SOURCES/$dist.tar.gz $dist
echo "done: $dist"

#rm -rf $dist

cat "$yamcshome/yamcs.spec" | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$REVISION\$/'$rev/ > $HOME/rpmbuild/SPECS/yamcs.spec
rpmbuild --define "_buildweb $buildweb" -ba $HOME/rpmbuild/SPECS/yamcs.spec

#echo "converting to deb (output is in /tmp)"
#sudo alien -c $HOME/rpmbuild/RPMS/noarch/$dist-5.noarch.rpm
