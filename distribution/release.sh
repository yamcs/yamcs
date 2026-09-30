#!/bin/bash
set -e

GPG_KEY=yamcs@spaceapplications.com
MAVEN_BUCKET=${MAVEN_BUCKET:-gs://yamcs-maven}

# Maven does not set <latest> in maven-metadata.xml (neither when creating it,
# nor when merging into it), so set it to the version just published, and
# update the checksums.
set_latest() {
    local file=$1 version=$2
    VERSION=$version perl -0pi -e '
        s#<latest>[^<]*</latest>#<latest>$ENV{VERSION}</latest># or
        s#(\s*)<versions>#$1<latest>$ENV{VERSION}</latest>$1<versions>#' "$file"
    write_checksums "$file"
}

write_checksums() {
    local file=$1
    for algorithm in md5 sha1 sha256 sha512; do
        printf '%s' "$(openssl dgst -$algorithm -r "$file" | cut -d' ' -f1)" > "$file.$algorithm"
    done
}

# The build attaches empty javadoc jars (see empty-javadoc profile). Replace
# them with the full javadoc jars, left unattached in each module's target/.
restore_full_javadoc() {
    local artifacts=$1
    for jar in `find $artifacts -name '*-javadoc.jar'`; do
        local artifactId=`basename $(dirname $(dirname $jar))`
        local full=`ls $clonedir/*/target/$artifactId-$pomversion-javadoc.jar 2>/dev/null | head -1`
        if [ -z "$full" ]; then
            echo "No full javadoc found for $artifactId" >&2
            exit 1
        fi
        cp "$full" "$jar"
        write_checksums "$jar"
        if [ -f "$jar.asc" ]; then
            gpg --batch --yes --local-user $GPG_KEY --armor --detach-sign "$jar"
            write_checksums "$jar.asc"
        fi
    done
}

prepare_yamcs_maven() {
    yamcs_maven_repo=releases
    if [ $snapshot -eq 1 ]; then
        yamcs_maven_repo=snapshots
    fi
    local remote="$MAVEN_BUCKET/$yamcs_maven_repo"

    if [ ! -d "$clonedir/yamcs-web/src/main/webapp/dist/webapp" ]; then
        echo 'The webapp is not built. Not publishing to maven.yamcs.org' >&2
        exit 1
    fi

    # Check before building, as the same build may also publish to Maven Central
    local existing
    if [ $snapshot -eq 0 ]; then
        if existing=$(gcloud storage ls "$remote/org/yamcs/*/$pomversion/" 2>&1); then
            echo "$pomversion is already published to maven.yamcs.org" >&2
            exit 1
        elif [[ $existing != *"matched no objects"* ]]; then
            echo "$existing" >&2
            exit 1
        fi
    fi

    yamcs_maven_staging=`mktemp -d`

    # Seed staging with the published metadata, so that Maven merges into it
    # rather than producing metadata with only this version.
    # (For snapshots, the version-level metadata holds the build number.)
    # One checksum is enough for Maven to validate the metadata.
    local patterns=("org/yamcs/*/maven-metadata.xml" "org/yamcs/*/maven-metadata.xml.sha1")
    if [ $snapshot -eq 1 ]; then
        patterns+=("org/yamcs/*/$pomversion/maven-metadata.xml" "org/yamcs/*/$pomversion/maven-metadata.xml.sha1")
    fi
    local listing=""
    for pattern in "${patterns[@]}"; do
        if ! listing+=$(gcloud storage ls "$remote/$pattern" 2>&1)$'\n'; then
            if [[ $listing != *"matched no objects"* ]]; then
                echo "$listing" >&2
                exit 1
            fi
        fi
    done
    echo "$listing" | grep "^$remote/" | while read -r url; do
        local path=${url#$remote/}
        mkdir -p "$yamcs_maven_staging/`dirname $path`"
        echo "$url" "$yamcs_maven_staging/$path"
    done | xargs -r -n 2 -P 8 gcloud storage cp --no-user-output-enabled

    yamcs_maven_goals=(
        org.apache.maven.plugins:maven-deploy-plugin:3.1.1:deploy
        -DaltDeploymentRepository=yamcs-maven::file://$yamcs_maven_staging
        -Daether.checksums.algorithms=SHA-512,SHA-256,SHA-1,MD5
    )
}

upload_to_yamcs_maven() {
    local staging=$yamcs_maven_staging
    local remote="$MAVEN_BUCKET/$yamcs_maven_repo"

    # Split what was deployed into artifacts and metadata. Metadata of other
    # artifacts (only there for seeding) is left out.
    local artifacts=$staging/upload/artifacts
    local metadata=$staging/upload/metadata
    for versiondir in `cd $staging && find org -type d -name "$pomversion"`; do
        local artifactdir=`dirname $versiondir`
        mkdir -p $artifacts/$artifactdir $metadata/$artifactdir
        mv $staging/$versiondir $artifacts/$artifactdir/
        mv $staging/$artifactdir/maven-metadata.xml* $metadata/$artifactdir/
        set_latest $metadata/$artifactdir/maven-metadata.xml $pomversion
        if [ $snapshot -eq 1 ]; then
            mkdir -p $metadata/$versiondir
            mv $artifacts/$versiondir/maven-metadata.xml* $metadata/$versiondir/
        fi
    done
    restore_full_javadoc $artifacts

    # Artifacts first, metadata last, so metadata never references
    # artifacts that are not uploaded yet.
    gcloud storage cp -r --no-clobber \
        --cache-control='public, max-age=31536000, immutable' \
        $artifacts/org "$remote/"
    gcloud storage cp -r \
        --cache-control='public, max-age=60' \
        $metadata/org "$remote/"

    rm -rf $staging
    echo "Published to https://maven.yamcs.org/$yamcs_maven_repo/org/yamcs/"
}

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
    version=${pomversion/-SNAPSHOT/}
    release="0.$(date '+%Y%m%d%H%M%S')"  # Generate unique sortable releases (for upgrade reasons)
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

mvn package -Drelease -DemptyJavadoc -DskipTests

rpmtopdir="$yamcshome/distribution/target/rpmbuild"
mkdir -p $rpmtopdir/{RPMS,BUILD,SPECS,tmp}

cp distribution/target/yamcs-$pomversion-* $yamcshome/distribution/target

# Build Yamcs RPM
rpmbuilddir="$rpmtopdir/BUILD/yamcs-$version-$release"

mkdir -p "$rpmbuilddir/opt/yamcs"
tar -xzf distribution/target/yamcs-$pomversion-linux-x86_64.tar.gz --strip-components=1 -C "$rpmbuilddir/opt/yamcs"

mkdir -p "$rpmbuilddir/usr/lib/systemd/system"
cp -a distribution/systemd/* "$rpmbuilddir/usr/lib/systemd/system"
cat distribution/rpm/yamcs.spec | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $rpmtopdir/SPECS/yamcs.spec

rpmbuild --target x86_64-linux --define="_topdir $rpmtopdir" -bb "$rpmtopdir/SPECS/yamcs.spec"

# Packet Viewer RPM
cp distribution/target/packet-viewer-$pomversion.tar.gz $yamcshome/distribution/target
rpmbuilddir="$rpmtopdir/BUILD/packet-viewer-$version-$release"
mkdir -p "$rpmbuilddir/opt/packet-viewer"
tar -xzf distribution/target/packet-viewer-$pomversion.tar.gz --strip-components=1 -C "$rpmbuilddir/opt/packet-viewer"
cat distribution/rpm/packet-viewer.spec | sed -e "s/@@VERSION@@/$version/" | sed -e "s/@@RELEASE@@/$release/" > $rpmtopdir/SPECS/packet-viewer.spec
rpmbuild --target noarch-linux --define="_topdir $rpmtopdir" -bb "$rpmtopdir/SPECS/packet-viewer.spec"

cd "$yamcshome"
mv distribution/target/rpmbuild/RPMS/*/* distribution/target/

if [ $snapshot -eq 0 ]; then
    rpmsign --key-id $GPG_KEY --addsign distribution/target/*.rpm
fi

echo
echo 'All done. Generated assets:'
ls -lh `find distribution/target -maxdepth 1 -type f`
echo

excluded_modules=$(cd $clonedir/examples && for d in */; do echo -n "!examples/${d%/},"; done)
excluded_modules="!examples,${excluded_modules%,}"


if [ $snapshot -eq 0 ]; then
    central='Maven Central'
else
    central='Sonatype Snapshots'
fi
echo "Where do you want to publish $pomversion maven artifacts?"
echo "  1) $central"
echo "  2) maven.yamcs.org"
echo "  3) Both"
echo "  4) Nowhere"
while true; do
    read -p "Choice [4]: " target
    target=${target:-4}
    if [[ $target =~ ^[1-4]$ ]]; then
        break
    fi
done

yamcs_maven_goals=()
if [[ $target == 2 || $target == 3 ]]; then
    prepare_yamcs_maven
fi
if [[ $target == 1 || $target == 3 ]]; then
    if [ $snapshot -eq 0 ]; then
        mvn -f $clonedir -Drelease -DemptyJavadoc -DskipTests -pl "$excluded_modules" -am deploy "${yamcs_maven_goals[@]}"
        echo 'Release the staging repository at https://central.sonatype.com'
    else
        mvn -f $clonedir -Drelease -DemptyJavadoc -DskipTests -DskipStaging -pl "$excluded_modules" -am deploy "${yamcs_maven_goals[@]}"
    fi
elif [[ $target == 2 ]]; then
    # Not the deploy phase, which would publish to Maven Central
    mvn -f $clonedir -Drelease -DemptyJavadoc -DskipTests -pl "$excluded_modules" -am verify "${yamcs_maven_goals[@]}"
fi
if [[ $target == 2 || $target == 3 ]]; then
    upload_to_yamcs_maven
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
