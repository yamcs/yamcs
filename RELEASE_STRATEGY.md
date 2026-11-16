# Pixxel Yamcs Release Strategy

This fork adds Pixxel features and bug fixes on top of
[upstream Yamcs](https://github.com/yamcs/yamcs). Its release cycle follows
upstream's development snapshot and stable release conventions, with Pixxel
changes integrated and validated before a fork release is published.

## Branches and remotes

`origin` refers to the Pixxel fork (`pixxelhq/yamcs`), and `upstream` refers to
the original repository (`yamcs/yamcs`).

| Branch or reference | Purpose | Update policy |
| --- | --- | --- |
| Fork `master` (`origin/master`) | Upstream release baseline, without Pixxel-specific changes. | Resync only when upstream publishes a release, using the selected release tag. |
| Fork `pixxel/master` (`origin/pixxel/master`) | Pixxel's development, integration, and release branch. | Receives reviewed feature and bug-fix PRs, plus upstream release integrations. |
| `upstream/master` | Local remote-tracking reference to upstream's development branch. | May advance when fetched; it can contain changes that have not been released. |

**`pixxel/master` will always be the default branch of this fork.** Create
feature and bug-fix branches from it and target PRs to it. Fork releases are
cut from `pixxel/master`; `master` exists to preserve the upstream baseline.

The release-only resync rule applies to the fork's `master` and the integration
of upstream changes into `pixxel/master`. It does not control upstream's own
`master` branch or prevent fetching remote references for inspection.

## Upstream synchronization

1. Wait for an upstream Yamcs release. Do not routinely merge upstream's
   unreleased `master` changes into the fork.
2. Select the upstream release tag, such as `yamcs-X.Y.Z`, and review its
   [release notes](https://docs.yamcs.org/yamcs-relnotes/), including compatibility
   changes and migration requirements.
3. Update the fork's `master` to that release baseline, preserving upstream
   history and keeping Pixxel changes out of this branch.
4. Open an integration PR from the updated fork `master` to `pixxel/master`.
   Resolve conflicts while retaining Pixxel features and fixes. Reconcile
   changes that upstream has independently fixed or implemented.
5. Validate the combined code before merging. Record the upstream tag and
   commit in the PR and in the next fork release notes.

Always integrate the selected release baseline rather than the latest
`upstream/master` tip, which may already include work for a later release.

## Development snapshots and stable releases

Working drafts use a version ending in **`-SNAPSHOT`**. Reviewed PRs intended
for a release are merged into `pixxel/master` during that development cycle.
Snapshots allow testing of the combined changes before the stable release.

Once all PRs selected for the release are merged and validation is complete,
remove the `-SNAPSHOT` suffix to produce the stable release version. Open or
unapproved PRs are deferred to a later release.

For example, the upstream-style lifecycle is:

```text
X.Y.Z-SNAPSHOT  ->  X.Y.Z  ->  X.Y.(Z+1)-SNAPSHOT
development        release   next development iteration
```

The upstream release used as the baseline must be recorded separately from
the fork's release version. A Pixxel release includes additional changes and
must be identifiable as a fork build in its release notes and package source.
If multiple fork releases use the same upstream baseline, assign each a new,
unique version; never overwrite an existing stable version or reuse its tag.

The root [pom.xml](pom.xml) uses `${revision}` for its version. Set the
`revision` property for the development or stable release and verify that all
module versions and internal dependencies resolve consistently. The version
transition above describes the policy; the inherited release script's
version-update commands must be checked against this property-based setup.

## Release checklist

1. Confirm that the release commit is on `pixxel/master`, the upstream baseline
   is documented, and all PRs selected for the release have been merged.
2. Prepare release notes listing the upstream release, Pixxel features and bug
   fixes, merged PRs, breaking changes, migration steps, and known limitations.
3. Build the web interface and run the Maven verification lifecycle, including
   tests, with the toolchain required by the current build configuration.
   Smoke-test affected Pixxel features and representative Yamcs configurations.
4. Remove `-SNAPSHOT`, commit the stable version, and validate the exact release
   commit before tagging and publishing it. Use a fork-specific tag namespace,
   for example `pixxel-yamcs-<version>`, to distinguish it from upstream tags.
5. Publish artifacts from that tagged commit to the fork's package registry and
   create a GitHub release containing the release notes and source tag. Ensure
   the published artifact version matches the tag's release version.
6. Advance `pixxel/master` to the next development version with `-SNAPSHOT`.
   Keep published stable artifacts and tags immutable.

## Publishing and existing tooling

Both snapshot and stable Maven publication are configured for
[Pixxel's GitHub Packages registry](https://github.com/pixxelhq/yamcs/packages).
The manually triggered
[Maven Publish workflow](.github/workflows/gh-maven-registry-publish.yml)
builds and deploys artifacts from the selected Git reference. Select
`pixxel/master` for development snapshots and the validated fork release tag
for a stable publication.

The current workflow skips tests and does not create a release tag or GitHub
release. Complete validation and release preparation before dispatching it;
successful publication alone is not release validation.

Upstream's [release script](https://github.com/yamcs/yamcs/blob/master/distribution/release.sh)
provides the reference for snapshot detection, stable tagging, and starting
the next development iteration. The inherited local
[distribution/release.sh](distribution/release.sh) also contains upstream
signing identities, tag names, and publishing destinations. It must be adapted
before being used to publish Pixxel releases.

## Fixes after a release

Develop fixes through reviewed PRs targeting `pixxel/master`. When an urgent
fix must be released before the next normal cycle, give it a new version and
apply the same validation, tagging, and release-note requirements. Such a
release can use the existing upstream baseline; it does not require syncing
unreleased upstream changes. If a maintenance branch is needed for an older
release, also integrate the applicable fix into `pixxel/master`.
