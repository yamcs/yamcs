#!/bin/bash

buildweb=1

if [ "$1" = "-noweb" ]; then
   buildweb=0
fi

cd `dirname $0`
yamcshome=`pwd`
pomversion=`grep -m 1 '<version>.*</version>' pom.xml | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/'`

#change x.y.z-SNAPSHOT into x.y.z_SNAPSHOT because "-" is not allowed in RPM version names
d=`date +%Y%m%d%H%M%S`
version=${pomversion/-SNAPSHOT/_SNAPSHOT$d}

rev=`git rev-parse --short HEAD`

serverdist=yamcs-${version}+r$rev

rm -rf /tmp/$serverdist
mkdir /tmp/$serverdist

git clone . /tmp/$serverdist
rm -rf /tmp/$serverdist/.git
cd /tmp/$serverdist

# fix revision in pom.xml
for f in `find . -name pom.xml`; do
    cat $f | sed -e 's/<version>'$version'/<version>'$version'-'$rev/ | sed -e 's/-\${buildNumber}/'/ >$f.fixed
    mv $f.fixed $f
done

# fix the default location of the server logs
logproperties=yamcs-core/etc/logging.properties.sample
sed -e 's/%h\/.yamcs\/log/\/opt\/yamcs\/log/g' $logproperties > $logproperties.tmp;
mv $logproperties.tmp $logproperties

if [[ $buildweb -ne 0 ]]; then
    cd yamcs-web
    yarn install
    yarn build
    rm -rf `find . -maxdepth 3 -name node_modules`
    cd ..
fi

mvn clean compile package -Dmaven.test.skip=true -Dmaven.buildNumber.doUpdate=false

mkdir -p dist

mkdir -p $HOME/rpmbuild/{RPMS,BUILD,SPECS,tmp}

# Server RPM
rm -rf "$HOME/rpmbuild/BUILD/$serverdist"
cp -r "/tmp/$serverdist" "$HOME/rpmbuild/BUILD/"
cat "$yamcshome/contrib/rpm/yamcs.spec" | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$REVISION\$/'$rev/ > $HOME/rpmbuild/SPECS/yamcs.spec
rpmbuild -bb $HOME/rpmbuild/SPECS/yamcs.spec

# Simulation RPM
simdist=yamcs-simulation-${version}+r$rev
rm -rf "$HOME/rpmbuild/BUILD/$simdist"
cp -r "/tmp/$serverdist" "$HOME/rpmbuild/BUILD/$simdist"
cat "$yamcshome/contrib/rpm/yamcs-simulation.spec" | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$REVISION\$/'$rev/ > $HOME/rpmbuild/SPECS/yamcs-simulation.spec
rpmbuild -bb $HOME/rpmbuild/SPECS/yamcs-simulation.spec

# Client (tar.gz, zip, RPM)
clientdist=yamcs-client-${version}+r$rev
rm -rf /tmp/$clientdist
mkdir -p /tmp/$clientdist/{bin,etc,lib,mdb}
cp /tmp/$serverdist/yamcs-client/bin/* /tmp/$clientdist/bin/
cp /tmp/$serverdist/yamcs-client/etc/* /tmp/$clientdist/etc/
cp /tmp/$serverdist/yamcs-client/target/yamcs-client-$pomversion.jar /tmp/$clientdist/lib/
cp /tmp/$serverdist/yamcs-client/lib/*.jar /tmp/$clientdist/lib/

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
ls -lh dist/*${version}+r$rev*
