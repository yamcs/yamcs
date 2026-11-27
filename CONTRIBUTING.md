# Contributing to Pixxel Yamcs

Standards for everyone changing this repo, human or AI agent. Branching and
releases: [RELEASE_STRATEGY.md](RELEASE_STRATEGY.md).

## Contents

- [Workflow](#workflow)
- [Hard rules](#hard-rules)
- [Coding](#coding)
- [Testing](#testing)
- [Validation before merge](#validation-before-merge)
- [Docs](#docs)
- [References](#references)
- [Commands](#commands)

## Workflow

Two developers work on this fork, so the process is light:

- Branch from `pixxel/master` (`feat/<topic>`, `fix/<topic>`) and open a PR
  back to it.
- Self-merge is fine once [validation](#validation-before-merge) passes. Ask
  the other developer to look first when the change touches a wire format,
  shared config, or upstream-owned files.
- Commit messages: `type(scope): summary`, e.g. `fix(pus): preserve wire APIDs`.
  Types: `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `chore`, `release`.
- Issue or ticket numbers are optional. Add one if it helps; none is required.
- Every PR has a short description:

  ```markdown
  ## Summary
  2–5 bullets: what changed and why, in plain words.

  ## Testing
  Commands run and their result (see Validation before merge).

  ## Notes (optional)
  Docs updated, assumptions logged, follow-ups, anything the reviewer should check.
  ```

## Hard rules

These are the only non-negotiables:

1. No secrets or real key material in code, config, or test resources.
2. A bug fix comes with a test that fails without the fix.
3. Changing a wire format updates every reader of it in the same PR (core,
   simulator, `tests/`, `examples/`, web).
4. Docs change in the same PR as the behaviour they describe ([Docs](#docs)).

## Coding

**Java**
- Use the Java release in the root `pom.xml` (25). No preview features.
- Format with [`eclipse-yamcs-style.xml`](eclipse-yamcs-style.xml). In upstream
  files, format only the lines you change; whole-file reformats break upstream
  merges.
- Log with `org.yamcs.logging.Log`, not `System.out`.
- Declare every new service or link option in `getSpec()`.
- Don't swallow exceptions. Error messages name the field, APID, or command.
- No hard-coded mission values (APIDs, SPIs, subtypes) in core code; use config
  or the MDB.

**Upstream merges**
- Prefer new classes or config over editing upstream classes.
- Mark edits inside upstream files with `// PIXXEL: <why>`.

**Binary data and time**
- Be explicit about byte order, bit width, and unsigned handling. Don't assume
  byte alignment.
- Keep generation time, reception time, and mission time separate. Stored TM
  (STM, dumped later in a pass) carries old generation times, so stamp events
  and alarms with packet generation time, not mission time.

**Ground vs simulator**
- Ground features are designed from the standards and mission ICDs. The
  simulator is a test tool: when it conflicts with or lacks what the ground
  design needs, change the simulator and example MDBs, not `yamcs-core`.

**Web**: `npm run prettier:check` passes in `yamcs-web/src/main/webapp`.

## Testing

- JUnit 5 only. Prefer real objects or small fakes over mocks.
- No `Thread.sleep` for synchronisation, no wall-clock, network, or local-path
  dependencies.
- Test wire formats at byte level, with a comment citing the spec clause.

**Test MDBs are temporary.** The MDBs under `examples/` and `tests/` are
currently AI-generated. They will soon be replaced by MDBs generated with the
mission tooling, for consistency. Until then:
- Don't treat them as a source of mission truth (APIDs, field widths, service
  subtypes). Check the mission ICD instead.
- Keep edits to them minimal and limited to what a test needs. Don't build
  features around their quirks.
- When the generated MDBs land, port tests to them and delete the AI-generated
  ones rather than keeping both.

| Kind | Location | Name |
| --- | --- | --- |
| Unit | `<module>/src/test/java/<same package>` | `<Class>Test` |
| Integration | `tests/src/test/java/org/yamcs/<area>/`, extends `AbstractIntegrationTest` | `<Feature>IntegrationTest` |
| End-to-end | `examples/<example>/tests/` | `test-<service>.py` |

## Validation before merge

No CI runs tests yet, so run these yourself and paste the results in the PR:

1. `mvn install` for the changed modules plus `tests` (or the whole build).
2. Web changes: `npm run prettier:check` and `npm run build`.
3. Link, frame, packet, PUS, or SDLS changes: run the relevant
   `examples/*/tests` script against the simulator.
4. After merging `pixxel/master` into your branch: a clean merge is not a
   verified merge. For each incoming change to a wire layout or config key,
   update branch-only readers of it, then repeat steps 1 and 3.

## Docs

Every feature built or changed updates its docs in the same PR. Skip only for
pure refactors, test-only, or internal changes, and say so in the PR.

| You changed | Update |
| --- | --- |
| `PusCommandPostprocessor` or its options | `docs/server-manual/links/command-postprocessor/pus.rst` |
| `PusPacketPreprocessor` or its options | `docs/server-manual/links/packet-preprocessor/pus.rst` |
| `PusEventDecoder` | `docs/server-manual/services/instance/pus-event-decoder.rst` |
| A PUS service (ST[n]) | `pus_analysis/pus<NN>.md` and `pus_analysis/README.md` |
| PUS architecture / simulator | `pus_analysis/pus_native_arch.md`, `pus_analysis/pus_simulator_architecture.md`, `examples/pus*/README.md` |
| Any other feature | the matching page under `docs/server-manual/` or `docs/http-api/`; if none fits, add one and link it from the section index |
| Release-visible behaviour | `Changelog` |

- Find affected pages: `git grep -il "<ClassName>" -- docs pus_analysis examples`.
- `.rst` pages document config options (type, default) and user-visible
  behaviour. `pus_analysis/*.md` document how the code behaves; cite classes
  and methods, not line numbers.
- Design specs and plans go in `docs/superpowers/{specs,plans}/YYYY-MM-DD-<topic>.md`,
  with a `## Contents` list kept in sync with the headings.

**Decisions and assumptions log**: record every assumption made while
building a feature (field widths, ICD ambiguities, placeholder values) in
`docs/decisions-and-assumptions.md`, one `## <Feature>` section each. Entry:
ID (`<PREFIX>-<n>`), assumption, why, what it affects, status (`Assumed`,
`Confirmed <date>`, `Deferred`, `Wrong, fixed <date>`), and owner. When the
real answer arrives, update the status and fix what it affects.

## References

- Code implementing a rule from a standard cites document and clause:
  `// ECSS-E-ST-70-41C §6.11.4.2: <rule>`. Mark intentional deviations with
  `// DEVIATION (<doc> §<clause>): <why>`.
- Mission ICDs, requirements, and PRDs live in a separate mission repo and
  change often. Re-read the relevant file before relying on it; don't trust
  older summaries.
- When porting an upstream fix, mention the upstream PR or commit in the commit
  body if you have it.

Standards in use (confirm the issue against the team's copy):

| Area | Document |
| --- | --- |
| PUS services | ECSS-E-ST-70-41C |
| Space packets | CCSDS 133.0-B-2 |
| TM / TC / AOS frames | CCSDS 132.0-B-3 / 232.0-B-4 / 732.0-B-4 |
| SDLS / SDLS Extended Procedures | CCSDS 355.0-B-2 / 355.1-B-1 |
| Time codes | CCSDS 301.0-B-4 |
| MDB | OMG XTCE 1.2 ([`docs/SpaceSystemV1.2.xsd`](docs/SpaceSystemV1.2.xsd)) |

## Commands

```bash
mvn install -DskipTests                           # build all (make build-skip-test)
mvn install -DskipTests -pl yamcs-core -am        # build one module
mvn test -pl yamcs-core -am -Dtest=PusCommandPostprocessorTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -pl tests -am test -Dtest=PusIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false

mvn -f examples/pus/pom.xml yamcs:run             # PUS example + embedded simulator, UI on :8090
./run-example.sh pus-frames --data-dir yamcs-data # frame-level PUS example

# Web dev server on :4200, proxying the API to :8090
cd yamcs-web/src/main/webapp && npx ng serve webapp --proxy-config proxy.dev.json
```
