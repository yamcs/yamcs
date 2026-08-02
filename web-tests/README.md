# Web UI tests

End-to-end tests for the Yamcs web interface, driven by
[Playwright for Java](https://playwright.dev/java/).

These tests boot a real `YamcsServer` in-process, serve the compiled webapp, and
drive it with a headless browser. They are ordinary JUnit 5 tests, so they run
under `mvn test` like everything else — no separate test runner, and no
JavaScript test tooling.

## Running

The tests need a compiled webapp. They are **skipped automatically** when one is
not present, so `mvn test` remains unaffected for anyone who has not built it:

    cd yamcs-web/src/main/webapp
    npm install
    npm run build
    cd -

    mvn -pl web-tests test

Activation mirrors the trick already used by the `yamcs-web` module: a profile
keyed on the existence of `yamcs-web/src/main/webapp/dist/webapp`.

## How it works

| Concern | Mechanism |
| --- | --- |
| Serving the webapp | `-Dyamcs.web.staticRoot=<dist>`, an override `WebFileDeployer` already supports for local development. No jar repackaging needed. |
| Registering the web UI | `yamcs-web` on the test classpath; `PluginManager` discovers `WebPlugin` via `ServiceLoader`. |
| Booting the server | `YConfiguration.setupTest(null)` + `YamcsServer.prepareStart()/start()`, the same pattern as `AbstractIntegrationTest`. |
| Mission database | The `examples/simulation` MDB (`simulator-ccsds.xls` + `landing.xls`), which has enumerated, aggregate, array and binary command arguments. |
| Browser | Reuses a browser from `PLAYWRIGHT_BROWSERS_PATH` when set; otherwise Playwright fetches its own. |

## Browsers in CI

Playwright for Java bundles its own driver (including a private Node runtime)
inside the jar, so no system Node install is required. Browsers are downloaded
on first use into `~/.cache/ms-playwright` and should be cached between runs:

    - uses: actions/cache@v4
      with:
        path: ~/.cache/ms-playwright
        key: playwright-${{ hashFiles('web-tests/pom.xml') }}

Set `-Dplaywright.skipBrowserDownload=1` to force use of an already-installed
browser instead.
