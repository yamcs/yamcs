#!/bin/bash
buildweb=1

if [ "$1" = "-noweb" ] ; then
   buildweb=0
fi

cd `dirname $0`
yamcshome=`pwd`
pomversion=`grep -m 1 '<version>.*</version>' pom.xml | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/'`

mkdir -p dist

#change x.y.z-SNAPSHOT into x.y.z_SNAPSHOT because "-" is not allowed in RPM version names
d=`date +%Y%m%d%H%M%S`
version=${pomversion/-SNAPSHOT/_SNAPSHOT$d}

rev=`git rev-parse --short HEAD`

dist=yamcs-${version}+r$rev

rm -rf /tmp/$dist
mkdir /tmp/$dist
 
git clone . /tmp/$dist
rm -rf /tmp/$dist/.git
cd /tmp/$dist

# fix revision in pom.xml
for f in pom.xml yamcs-core/pom.xml yamcs-client/pom.xml yamcs-server/pom.xml yamcs-api/pom.xml yamcs-xtce/pom.xml yamcs-artemis/pom.xml yamcs-simulation/pom.xml; do
    cat $f | sed -e 's/<version>'$version'/<version>'$version'-'$rev/ | sed -e 's/-\${buildNumber}/'/ >$f.fixed
    mv $f.fixed $f
done

# fix the default location of the server logs
logproperties=yamcs-core/etc/logging.properties.sample
sed -e 's/%h\/.yamcs\/log/\/opt\/yamcs\/log/g' $logproperties > $logproperties.tmp;
mv $logproperties.tmp $logproperties

cd /tmp

mkdir -p $HOME/rpmbuild/{SRPMS,RPMS,BUILD,SPECS,SOURCES,tmp}

echo "Packing up sources..."
tar czfh $HOME/rpmbuild/SOURCES/$dist.tar.gz $dist
echo "done: $dist"

#rm -rf $dist

# Server RPM
cat "$yamcshome/contrib/rpm/yamcs.spec" | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$REVISION\$/'$rev/ > $HOME/rpmbuild/SPECS/yamcs.spec
rpmbuild --define "_buildweb $buildweb" -ba $HOME/rpmbuild/SPECS/yamcs.spec

# Simulation RPM
simdist=yamcs-simulation-${version}+r$rev
rm -rf "$HOME/rpmbuild/BUILD/$simdist"
cp -r "$HOME/rpmbuild/BUILD/$dist" "$HOME/rpmbuild/BUILD/$simdist"
cat "$yamcshome/contrib/rpm/yamcs-simulation.spec" | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$REVISION\$/'$rev/ > $HOME/rpmbuild/SPECS/yamcs-simulation.spec
rpmbuild -bb $HOME/rpmbuild/SPECS/yamcs-simulation.spec

# Client (tar.gz, zip, RPM)
clientdist=yamcs-client-${version}+r$rev
rm -rf /tmp/$clientdist
mkdir -p /tmp/$clientdist/{bin,etc,lib,mdb}
cp $yamcshome/yamcs-client/bin/* /tmp/$clientdist/bin/
cp $yamcshome/yamcs-client/etc/* /tmp/$clientdist/etc/
cp $yamcshome/yamcs-client/target/yamcs-client-$pomversion.jar /tmp/$clientdist/lib/
cp $yamcshome/yamcs-client/lib/*.jar /tmp/$clientdist/lib/

cd /tmp
tar czfh $yamcshome/dist/$clientdist.tar.gz $clientdist
zip -r $yamcshome/dist/$clientdist.zip $clientdist

rm -rf "$HOME/rpmbuild/BUILD/$clientdist"
cp -r "/tmp/$clientdist" "$HOME/rpmbuild/BUILD/"
cat "$yamcshome/contrib/rpm/yamcs-client.spec" | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$REVISION\$/'$rev/ > $HOME/rpmbuild/SPECS/yamcs-client.spec
rpmbuild -bb $HOME/rpmbuild/SPECS/yamcs-client.spec

rm -rf /tmp/$clientdist

cd "$yamcshome"
mv $HOME/rpmbuild/RPMS/noarch/*${version}+r$rev* dist/

rpmsign --key-id yamcs@spaceapplications.com --addsign dist/*${version}+r$rev*.rpm
ls -l dist/*${version}+r$rev*
