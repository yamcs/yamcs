import { defineConfig, devices } from '@playwright/test';
import * as path from 'path';

// e2e -> webapp -> main -> src -> yamcs-web -> repository root
const REPO_ROOT = path.resolve(__dirname, '../../../../..');

const PORT = 8090;
const BASE_URL = `http://localhost:${PORT}`;

// examples/simulation pins dataDir to /storage/yamcs-data, which does not exist
// on a stock machine (and cannot easily be created on macOS). Yamcs takes
// --data-dir/--cache-dir ahead of the yaml, and run-example.sh forwards any
// extra arguments, so the run is redirected into a gitignored scratch dir.
const RUNTIME_DIR = path.join(__dirname, '.cache');

export default defineConfig({
  testDir: './tests',
  // Keep run artifacts inside e2e/ rather than wherever the runner was invoked
  outputDir: './test-results',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI
    ? [
        ['github'],
        ['html', { outputFolder: './playwright-report', open: 'never' }],
      ]
    : [['list']],
  globalSetup: './global-setup',
  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    // Boots a real Yamcs against the npm-built webapp. examples/pom.xml
    // already passes -Dyamcs.web.staticRoot=.../dist/webapp to yamcs:run, so
    // this serves whatever 'npm run build' last produced.
    command:
      `./run-example.sh simulation` +
      ` --data-dir ${path.join(RUNTIME_DIR, 'data')}` +
      ` --cache-dir ${path.join(RUNTIME_DIR, 'cache')}`,
    cwd: REPO_ROOT,
    url: `${BASE_URL}/api`,
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
