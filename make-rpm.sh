#!/bin/bash

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
for f in pom.xml yamcs-core/pom.xml yamcs-api/pom.xml yamcs-xtce/pom.xml yamcs-web/pom.xml yamcs-simulation/pom.xml; do
    cat $f | sed -e 's/'$version'/'$version'-'$rev/ | sed -e 's/-\${buildNumber}/'/ >$f.fixed
    mv $f.fixed $f
done

# fix a few configuration files
cd yamcs-core/etc

for file in $( find . -name "*.properties" -type f )
do
	sed -e 's/%h\/.yamcs\/log/\/opt\/yamcs\/log/g' $file > $file.tmp;
	mv $file.tmp $file	
done

cd /tmp

mkdir -p $HOME/rpmbuild/{SRPMS,RPMS,BUILD,SPECS,SOURCES,tmp}

tar czfh $HOME/rpmbuild/SOURCES/$dist.tar.gz $dist

echo $dist

#rm -rf $dist

cat $yamcshome/yamcs.spec | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$REVISION\$/'$rev/ > $HOME/rpmbuild/SPECS/yamcs.spec
rpmbuild -ba $HOME/rpmbuild/SPECS/yamcs.spec

#echo "converting to deb (output is in /tmp)"
#sudo alien -c $HOME/rpmbuild/RPMS/noarch/$dist-5.noarch.rpm
