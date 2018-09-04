#!/bin/bash
set -e

# This script generates releases in two steps:
# 1. Compile a fresh clone of the development tree (somewhere under /tmp)
# 2. Emit artifacts (yamcs, yamcs-simulation, yamcs-client) in various formats.
#
# By design (2) does not require any sort of compilation.

buildweb=1
sign=1

all=1
yamcs=0
yamcsclient=0
yamcssimulation=0

for arg in "$@"; do
    case "$arg" in
    --no-web)
        buildweb=0
        ;;
    --no-sign)
        sign=0
        ;;
    yamcs)
        all=0
        yamcs=1
        ;;
    yamcs-client)
        all=0
        yamcsclient=1
        ;;
    yamcs-simulation)
        all=0
        yamcssimulation=1
        ;;
    *)
        echo "Usage: $0 [--no-web] [--no-sign] [yamcs|yamcs-client|yamcs-simulation]..."
        exit 1;
        ;;
    esac
done

if [ $all -eq 1 ]; then
    yamcs=1
    yamcsclient=1
    yamcssimulation=1
fi

cd `dirname $0`
yamcshome=`pwd`
pomversion=`grep -m 1 '<version>.*</version>' pom.xml | sed -e 's/.*<version>\(.*\)<\/version>.*/\1/'`

if [[ $pomversion == *-SNAPSHOT ]]; then
    d=`date +%Y%m%d%H%M%S`
    version=${pomversion/-SNAPSHOT/}
    release=SNAPSHOT$d.`git rev-parse --short HEAD`
else
    version=$pomversion
    release=1.`git rev-parse --short HEAD`
fi

if [[ -n $(git status -s) ]]; then
    read -p 'Your workspace contains dirty or untracked files. These will not be part of your release. Continue? [Y/n]' yesNo
    if [[ -n $yesNo ]] && [[ $yesNo == 'n' ]]; then
        exit 0
    fi
fi

buildroot=/tmp/yamcs-$version-$release-buildroot

serverdist=yamcs-$version-$release
simdist=yamcs-simulation-$version-$release
clientdist=yamcs-client-$version-$release

rm -rf $buildroot
mkdir $buildroot
git clone . $buildroot
rm -rf $buildroot/.git

cd $buildroot

if [ $yamcs -eq 1 -a $buildweb -eq 1 ]; then
    cd yamcs-web
    yarn install
    yarn build
    rm -rf `find . -maxdepth 3 -name node_modules`
    cd ..
fi

mvn clean package -DskipTests

mkdir -p $yamcshome/dist
mkdir -p $HOME/rpmbuild/{RPMS,BUILD,SPECS,tmp}

if [ $yamcs -eq 1 ]; then
    rm -rf /tmp/$serverdist
    mkdir -p /tmp/$serverdist/{bin,cache,etc,lib,lib/ext,log,mdb}
    cp -a yamcs-server/bin/* /tmp/$serverdist/bin/
    cp -a yamcs-core/etc/* /tmp/$serverdist/etc/
    cp -a yamcs-server/lib/* /tmp/$serverdist/lib/
    cp -a yamcs-api/src/main/*.proto /tmp/$serverdist/lib/
    cp -a yamcs-server/target/yamcs*.jar /tmp/$serverdist/lib/
    cp -a yamcs-artemis/lib/*.jar /tmp/$serverdist/lib/
    cp -a yamcs-artemis/target/yamcs-artemis*.jar /tmp/$serverdist/lib/
    rm -f /tmp/$serverdist/lib/*-sources.jar

    if [ $buildweb -eq 1 ]; then
        mkdir -p /tmp/$serverdist/lib/yamcs-web
        cp -a yamcs-web/packages/app/dist/* /tmp/$serverdist/lib/yamcs-web/
    fi

    cd /tmp
    tar czfh $yamcshome/dist/$serverdist.tar.gz $serverdist

    rpmbuilddir="$HOME/rpmbuild/BUILD/$serverdist"
    rm -rf $rpmbuilddir
    mkdir -p "$rpmbuilddir/opt/yamcs"
    cp -a /tmp/$serverdist/* "$rpmbuilddir/opt/yamcs"
    mkdir -p "$rpmbuilddir/etc/init.d"
    cp -a $yamcshome/contrib/sysvinit/* "$rpmbuilddir/etc/init.d"
    cat "$yamcshome/contrib/rpm/yamcs.spec" | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $HOME/rpmbuild/SPECS/yamcs.spec
    rpmbuild -bb $HOME/rpmbuild/SPECS/yamcs.spec
    
    rm -rf /tmp/$serverdist
fi

if [ $yamcssimulation -eq 1 ]; then
    rm -rf "$HOME/rpmbuild/BUILD/$simdist"
    cp -r $buildroot "$HOME/rpmbuild/BUILD/$simdist"
    cat "$yamcshome/contrib/rpm/yamcs-simulation.spec" | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $HOME/rpmbuild/SPECS/yamcs-simulation.spec
    rpmbuild -bb $HOME/rpmbuild/SPECS/yamcs-simulation.spec
fi

if [ $yamcsclient -eq 1 ]; then
    cd $buildroot
    rm -rf /tmp/$clientdist
    mkdir -p /tmp/$clientdist/{bin,etc,lib,mdb}
    cp yamcs-client/bin/* /tmp/$clientdist/bin/
    cp yamcs-client/etc/* /tmp/$clientdist/etc/
    cp yamcs-client/target/yamcs-client-$pomversion.jar /tmp/$clientdist/lib/
    cp yamcs-client/lib/*.jar /tmp/$clientdist/lib/

    cd /tmp
    tar czfh $yamcshome/dist/$clientdist.tar.gz $clientdist

    rm -rf "$HOME/rpmbuild/BUILD/$clientdist"
    cp -r "/tmp/$clientdist" "$HOME/rpmbuild/BUILD/"
    cp -a $yamcshome/contrib/completion "$HOME/rpmbuild/BUILD/$clientdist"
    cat "$yamcshome/contrib/rpm/yamcs-client.spec" | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $HOME/rpmbuild/SPECS/yamcs-client.spec
    rpmbuild -bb $HOME/rpmbuild/SPECS/yamcs-client.spec

    rm -rf /tmp/$clientdist
fi

rm -rf $buildroot

cd "$yamcshome"
mv $HOME/rpmbuild/RPMS/noarch/*$version-$release* dist/

if [ $sign -eq 1 ]; then
    rpmsign --key-id yamcs@spaceapplications.com --addsign dist/*$version-$release*.rpm
fi

echo
echo 'All done. Generated artifacts:'
ls -lh dist/*$version-$release*
