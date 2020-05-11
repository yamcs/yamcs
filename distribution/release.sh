#!/bin/bash
set -e

GPG_KEY=yamcs@spaceapplications.com

cd `dirname $0`/..
yamcshome=`pwd`

if [[ -n $(git status -s) ]]; then
    read -p 'Your workspace contains dirty or untracked files. These will not be part of your release. Continue? [Y/n] ' yesNo
    if [[ -n $yesNo ]] && [[ $yesNo == 'n' ]]; then
        exit 0
    fi
fi

pomversion=`mvn -q help:evaluate -Dexpression=project.version -DforceStdout`
read -p "Enter the new version to set [$pomversion] " newVersion
if [[ -n $newVersion ]]; then
    pomversion=$newVersion
    mvn versions:set -DnewVersion=$newVersion versions:commit
fi

if [[ $pomversion == *-SNAPSHOT ]]; then
    snapshot=1
    d=`date +%Y%m%d%H%M%S`
    version=${pomversion/-SNAPSHOT/}
    release=SNAPSHOT$d
else
    snapshot=0
    version=$pomversion
    release=1  # Incremental release number for a specific version
fi

if [[ -n $(git status -s) ]]; then
    git commit . -v -em"Prepare release yamcs-${version}" || :
    if [ $snapshot -eq 0 ]; then
        git tag yamcs-$version
    fi
fi

mvn -q clean

clonedir=$yamcshome/distribution/target/yamcs-clone

mkdir -p $clonedir
git clone . $clonedir
rm -rf $clonedir/.git

cd $clonedir

cd yamcs-web/src/main/webapp
npm install
npm run build
rm -rf node_modules
cd -

mvn package -P yamcs-release -DskipTests

rpmtopdir="$yamcshome/distribution/target/rpmbuild"
mkdir -p $rpmtopdir/{RPMS,BUILD,SPECS,tmp}

# Build Yamcs RPM
cp distribution/target/yamcs-$pomversion.tar.gz $yamcshome/distribution/target

rpmbuilddir="$rpmtopdir/BUILD/yamcs-$version-$release"

mkdir -p "$rpmbuilddir/opt/yamcs"
tar -xzf distribution/target/yamcs-$pomversion-linux64.tar.gz --strip-components=1 -C "$rpmbuilddir/opt/yamcs"

mkdir -p "$rpmbuilddir/etc/init.d"
cp -a distribution/sysvinit/* "$rpmbuilddir/etc/init.d"
mkdir -p "$rpmbuilddir/usr/lib/systemd/system"
cp -a distribution/systemd/* "$rpmbuilddir/usr/lib/systemd/system"
cat distribution/rpm/yamcs.spec | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $rpmtopdir/SPECS/yamcs.spec

rpmbuild --define="_topdir $rpmtopdir" -bb "$rpmtopdir/SPECS/yamcs.spec"

# Simulation Example RPM
cp -r $clonedir "$rpmtopdir/BUILD/yamcs-simulation-$version-$release"
cat distribution/rpm/yamcs-simulation.spec | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $rpmtopdir/SPECS/yamcs-simulation.spec
rpmbuild --define="_topdir $rpmtopdir" -bb "$rpmtopdir/SPECS/yamcs-simulation.spec"

# Packet Viewer RPM
cp distribution/target/packet-viewer-$pomversion.tar.gz $yamcshome/distribution/target
rpmbuilddir="$rpmtopdir/BUILD/packet-viewer-$version-$release"
mkdir -p "$rpmbuilddir/opt/packet-viewer"
tar -xzf distribution/target/packet-viewer-$pomversion.tar.gz --strip-components=1 -C "$rpmbuilddir/opt/packet-viewer"
cat distribution/rpm/packet-viewer.spec | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $rpmtopdir/SPECS/packet-viewer.spec
rpmbuild --define="_topdir $rpmtopdir" -bb "$rpmtopdir/SPECS/packet-viewer.spec"

cd "$yamcshome"
mv distribution/target/rpmbuild/RPMS/noarch/* distribution/target/
mv distribution/target/rpmbuild/RPMS/x86_64/* distribution/target/

if [ $snapshot -eq 0 ]; then
    rpmsign --key-id $GPG_KEY --addsign distribution/target/*.rpm
fi

echo
echo 'All done. Generated assets:'
ls -lh `find distribution/target -maxdepth 1 -type f`
echo

if [ $snapshot -eq 0 ]; then
    read -p "Do you want to stage $pomversion maven artifacts to Maven Central? [y/N] " yesNo
    if [[ $yesNo == 'y' ]]; then
        mvn -f $clonedir -P yamcs-release -DskipTests deploy
        echo 'Release the staging repository at https://oss.sonatype.org'
    fi
else
    read -p "Do you want to publish $pomversion maven artifacts to Sonatype Snapshots? [y/N] " yesNo
    if [[ $yesNo == 'y' ]]; then
        mvn -f $clonedir -P yamcs-release -DskipTests -DskipStaging deploy
    fi
fi

rm -rf $clonedir $rpmtopdir

# Upgrade version in pom.xml files
# For example: 1.2.3 --> 1.2.4-SNAPSHOT
if [ $snapshot -eq 0 ]; then
    if [[ $version =~ ([0-9]+)\.([0-9]+)\.([0-9]+) ]]; then
        developmentVersion=${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.$((BASH_REMATCH[3] + 1))-SNAPSHOT
        mvn versions:set -DnewVersion=$developmentVersion versions:commit
        git commit . -v -em"Prepare next development iteration"
    else
        echo 'Failed to set development version'
        exit 1
    fi
fi
