import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './browser',
  globalSetup: './global-setup.mjs',
  use: { headless: true },
  // A click runs a whole ELK layout, and cytoscape-elk builds a fresh ELK for each one - about ten
  // seconds a click on these fixtures, so a two-click test sits just under the 30s default and fails
  // whenever a second worker is competing for the CPU. The libraries are byte-identical to the ones
  // this page used to load as script tags, so this is the suite's own cost and not the bundle's.
  timeout: 120_000,
  reporter: 'list',
});
