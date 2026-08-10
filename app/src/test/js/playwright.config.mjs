import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './browser',
  globalSetup: './global-setup.mjs',
  use: { headless: true },
  // No timeout override. This used to be 120s because cytoscape-elk built a fresh ELK for every
  // layout - about ten seconds a click, so a two-click test sat just under the 30s default and
  // failed whenever a second worker was competing for the CPU. The page now runs ELK itself and a
  // click costs a few hundred milliseconds, so the default is several times the slowest test.
  reporter: 'list',
});
