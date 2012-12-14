#!/bin/bash

cd `dirname $0`
yamcshome=`pwd`
version=`grep -m 1 '<version>.*</version>' pom.xml | sed -e 's/.*<version>\(.*\)\-SNAPSHOT<\/version>.*/\1/'`

if [ "$OSTYPE" = "darwin" ]; then
    svnrev=`svnversion yamcs-core | sed -Ee 's/(.*:)*([0-9]+)M?/\2/'`
else
    svnrev=`svnversion yamcs-core | sed -re 's/(.*:)*([0-9]+)M?/\2/'`
fi

dist=yamcs-${version}+SVNr$svnrev

cd /tmp
rm -rf $dist
mkdir $dist
cd $dist
 
for f in pom.xml yamcs-core yamcs-api yamcs-xtce yamcs-web; do
	svn export -r BASE $yamcshome/$f $f
done

# fix revision in pom.xml
for f in pom.xml yamcs-core/pom.xml yamcs-api/pom.xml yamcs-xtce/pom.xml yamcs-web/pom.xml; do
    cat $f | sed -e 's/'$version'-SNAPSHOT/'$version'-'$svnrev/ | sed -e 's/-\${buildNumber}/'/ >$f.fixed
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

rm -rf $dist

cat $yamcshome/yamcs.spec | sed -e 's/\$VERSION\$/'$version/ | sed -e 's/\$SVN_REVISION\$/'$svnrev/ > $HOME/rpmbuild/SPECS/yamcs.spec
rpmbuild -ba $HOME/rpmbuild/SPECS/yamcs.spec

#echo "converting to deb (output is in /tmp)"
#sudo alien -c $HOME/rpmbuild/RPMS/noarch/$dist-5.noarch.rpm
