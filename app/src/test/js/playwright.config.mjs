import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './browser',
  globalSetup: './global-setup.mjs',
  use: { headless: true },
  reporter: 'list',
});
