In order to be able to release, the following is required:

- An account at [Sonatype Central Portal](https://central.sonatype.com) with rights to the `org.yamcs` namespace.
- PGP key `yamcs@spaceapplications.com`

Credentials are configured in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>XXXX</username>
      <password>YYYY</password>
    </server>
  </servers>
</settings>
```

The GPG invocation should bring up a popup from the GPG agent asking for the passphrase (or it could also be automatically added to the agent at login). Alternatively, the passphrase can be configured using the `gpg.passphrase` and `gpg.keyname` properties in `settings.xml`.

Once all artifacts have been uploaded to Sonatype, the actual publishing of a new release is a manual step to be performed at https://central.sonatype.com.

## Snapshot builds

Snapshot builds do not require a manual publish step. They are immediately deployed to the Central Portal Snapshot Repository, where they are automatically removed after 90 days. Snapshot builds are not available from the central Maven repository, instead you can use them by adding this to a project's `pom.xml`:

```xml
<repositories>
  <repository>
    <name>Central Portal Snapshots</name>
    <id>central-portal-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases>
      <enabled>false</enabled>
    </releases>
    <snapshots>
      <enabled>true</enabled>
    </snapshots>
  </repository>
</repositories>
```
