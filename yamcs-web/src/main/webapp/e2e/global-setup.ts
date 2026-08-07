import type { FullConfig } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

const INSTANCE = 'simulator';
const WEBAPP_DIST = path.join(__dirname, '..', 'dist', 'webapp');
const READY_TIMEOUT = 120_000;
const POLL_INTERVAL = 1_000;

// examples/simulation sets 'yamcs-web: tag:' to this, and WebPlugin bakes it
// into the served index.html. It is the cheapest way to tell this example
// apart from some other Yamcs that happens to hold the port.
const EXPECTED_TAG = 'Example: simulation';

/**
 * Fails if the webapp has not been compiled.
 *
 * The -Dyamcs.web.staticRoot override only wins while the directory exists.
 * Without it Yamcs quietly falls back to the copy baked into the yamcs-web jar
 * at package time, and the suite then passes against a webapp that may bear no
 * relation to the working tree. That silent fallback is worth turning into an
 * explicit failure.
 */
function assertWebappBuilt() {
  if (!fs.existsSync(WEBAPP_DIST)) {
    throw new Error(
      `No compiled webapp at ${WEBAPP_DIST}.\n` +
        `Run 'npm run build' first: without it Yamcs serves the webapp bundled ` +
        `in the yamcs-web jar, and these tests would silently exercise that ` +
        `instead of the working tree.`,
    );
  }
}

/**
 * Blocks until the 'simulator' instance reports RUNNING.
 *
 * Yamcs starts its global services (the HTTP server among them) synchronously,
 * but instances are started with startAsync() and are never awaited. A probe
 * against /api therefore proves only that the server is listening: the mission
 * database may still be loading, and /api/instances/{instance} answers 200 with
 * state INITIALIZING well before the instance is usable. Waiting here keeps
 * that race out of the individual tests.
 */
async function waitForInstance(baseURL: string) {
  const url = `${baseURL}/api/instances/${INSTANCE}`;
  const deadline = Date.now() + READY_TIMEOUT;

  let lastSeen = 'no response yet';
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) {
        const { state } = await response.json();
        if (state === 'RUNNING') {
          return;
        }
        lastSeen = `state ${state}`;
      } else {
        lastSeen = `HTTP ${response.status}`;
      }
    } catch (err) {
      // Server not accepting connections yet.
      lastSeen = err instanceof Error ? err.message : String(err);
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL));
  }

  throw new Error(
    `Instance '${INSTANCE}' was not RUNNING within ${READY_TIMEOUT} ms ` +
      `(last seen: ${lastSeen}). Checked ${url}`,
  );
}

/**
 * Fails if the server on the port is not the simulation example.
 *
 * reuseExistingServer means anything already listening is adopted, and Yamcs
 * exits with BUILD SUCCESS when it cannot bind, so an unrelated server left
 * running locally would otherwise be tested silently and confusingly.
 */
async function assertExpectedServer(baseURL: string) {
  const html = await (await fetch(baseURL)).text();
  if (!html.includes(EXPECTED_TAG)) {
    throw new Error(
      `The server on ${baseURL} does not look like the simulation example ` +
        `(no '${EXPECTED_TAG}' in the served index).\n` +
        `Something else is probably holding the port: stop it and re-run.`,
    );
  }
}

async function globalSetup(config: FullConfig) {
  assertWebappBuilt();

  const baseURL = config.projects[0]?.use?.baseURL ?? 'http://localhost:8090';
  await waitForInstance(baseURL);
  await assertExpectedServer(baseURL);
}

export default globalSetup;
