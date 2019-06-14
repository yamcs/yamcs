Starting with 3.0.0, we use the standard maven release plugin configured to push to nexus:

    mvn release:prepare release:perform

In order for this to work, you need:
- Sonatype Nexus credentials. These can be used to login to oss.sonatype.org (as well as issues.sonatype.org)
- PGP key yamcs@spaceapplications.com

The two can be configured in `~/.m2/settings.xml`, see below.

In addition, the maven release plugin will create a tag and push it to github, so that should be possible from your account (using normal ssh key access to github).

The gpg invokation should bring up a popup from the gpg agent asking for the passphrase (or it might even be automatically added to the agent at login). Alternatively, the passphrase can be configured using the gpg.passphrase property next to the pgp.keyname in the settings.xml.

The maven release plugin makes a target/checkout directory with a clone of the repository for the tag (or you can get this version with `git checkout <version-tag>`). This directory can be used in case of trouble to modify the pom.xml and do manual things like:

    mvn deploy -B -P yamcs-release [-DskipTests] # this should perform the deployment to nexus - staging area.

The release has to be done manually by logging in to oss.sonatype.org (make sure that you are logged in!!), select from the left Staging Repositories, find something like orgyamcs-xyz in the list. The status should be closed (otherwise there was some error) and then you can press Release in the menubar.


### sample .m2/settings.xml
    <settings>
      <servers>
        <server>
          <id>ossrh</id>
          <username>XXXX</username>
          <password>YYYY</password>
        </server>
      </servers>
      <profiles>
        <profile>
          <id>yamcs-release</id>
          <properties>
            <gpg.keyname>ZZZZ</gpg.keyname>
          </properties>
        </profile>
      </profiles>
    </settings>
