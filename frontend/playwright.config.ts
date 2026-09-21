import { defineConfig, devices } from '@playwright/test';

const isCI = !!process.env['CI'];

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.spec.ts',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  // A ceiling on the whole suite in CI. Without it, 31 specs that each run into
  // the 30s test timeout, twice retried, keep a runner busy for the better part
  // of an hour before anyone sees a log. The suite takes well under a minute
  // when it is healthy, so this only ever fires on a broken run.
  globalTimeout: isCI ? 8 * 60_000 : undefined,
  // Readable console output plus an HTML report (uploaded as a CI artifact);
  // `open: never` keeps it from launching a browser locally on failure.
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  // Locally: start (or reuse) the dev server on 4200. In CI: serve the
  // production build instead (over `dist/frontend/browser`, built by the
  // workflow beforehand) — it catches production-only bugs. Same URL either
  // way, so the specs don't care which server answers.
  //
  // The CI branch calls the binary rather than the `serve:dist` script: a kill
  // at teardown reaches the package manager, not the server it spawned, and
  // Playwright then waits for a process that never ends. That branch only ever
  // runs on Linux, so the POSIX path is safe.
  webServer: {
    command: isCI ? './node_modules/.bin/serve --single --listen 4200 dist/frontend/browser' : 'pnpm start',
    url: 'http://localhost:4200',
    reuseExistingServer: !isCI,
    timeout: 120_000,
  },
});
