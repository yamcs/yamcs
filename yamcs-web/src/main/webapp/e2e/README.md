# Web UI tests

End-to-end tests for the Yamcs web interface, using
[`@playwright/test`](https://playwright.dev).

Proof of concept for [#1140](https://github.com/yamcs/yamcs/issues/1140). A
second, independent proof of concept using the Playwright Java bindings and
JUnit lives in [#1141](https://github.com/yamcs/yamcs/pull/1141).

## How it works

Playwright's `webServer` boots a real Yamcs via `run-example.sh simulation` and
drives it with headless Chromium. Nothing is mocked.

The `simulation` example is reused as-is because `examples/pom.xml` already
passes `-Dyamcs.web.staticRoot=.../dist/webapp` to `yamcs:run`. Yamcs therefore
serves whatever `npm run build` last produced, which is what makes these tests
exercise the working tree rather than a packaged jar.

Two adjustments are applied at launch:

- `--data-dir` / `--cache-dir` redirect the run into `e2e/.cache/`. The example
  otherwise writes to `/storage/yamcs-data`, which does not exist on a stock
  machine and cannot easily be created on macOS.
- `global-setup.ts` waits for the `simulator` instance to report `RUNNING`.
  Yamcs starts global services synchronously but instances asynchronously, so a
  probe against `/api` proves only that the HTTP server is listening — the
  mission database may still be loading.

The example ships no `security.yaml`, so authentication is disabled and the
built-in `guest` user is a superuser. There is no login step.

## Running

Requires the Yamcs artifacts in the local Maven repository:

    mvn -DskipTests install

Then, from `yamcs-web/src/main/webapp`:

    npm install
    npx playwright install chromium
    npm run build
    npm run e2e

`npm run build` matters. The `-Dyamcs.web.staticRoot` override only wins while
`dist/webapp` exists; without it Yamcs quietly serves the copy baked into the
`yamcs-web` jar at package time, and the suite passes against a webapp that may
bear no relation to the working tree. `global-setup.ts` turns that silent
fallback into an explicit failure, but a *stale* `dist/webapp` is still tested
as-is — rebuild before trusting a green run.

Useful variants:

    npm run e2e:ui                  # interactive UI mode
    npm run e2e -- --headed         # watch a real browser
    npm run e2e -- --debug          # step through with the inspector

A server already listening on 8090 is reused outside CI, which makes for a
much faster edit-run loop: leave `./run-example.sh simulation` running in one
terminal and re-run the tests in another. In CI a dedicated server is always
started.

## Layout

    playwright.config.ts   Server lifecycle, browser and reporter configuration
    global-setup.ts        Waits for the simulator instance to be RUNNING
    tests/                 Specs
