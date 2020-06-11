You need:

- Sonatype Nexus credentials. These can be used to login to oss.sonatype.org (as well as issues.sonatype.org)
- PGP key yamcs@spaceapplications.com

The two can be configured in `~/.m2/settings.xml`, see below.

The gpg invokation should bring up a popup from the gpg agent asking for the passphrase (or it might even be automatically added to the agent at login). Alternatively, the passphrase can be configured using the gpg.passphrase property next to the pgp.keyname in the settings.xml.

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
    </settings>
