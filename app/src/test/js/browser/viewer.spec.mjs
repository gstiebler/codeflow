import { test, expect } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('build/viewer-test/funcCall.html')).href;

/** Cytoscape is on window and holds the graph; this is how the tests see what rendered. */
const counts = (page) => page.evaluate(() => ({
  nodes: window.cy.nodes().length,
  edges: window.cy.edges().length,
  methods: window.cy.nodes('[type = "METHOD"]').length,
}));

test.beforeEach(async ({ page }) => {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(PAGE);
  // Checking the type, not just presence: `!== undefined` would be satisfied by anything at all,
  // and this guard is the only thing standing between a page that never initialised and a green run.
  await page.waitForFunction(() => typeof window.cy?.nodes === 'function');
  expect(errors, 'the page threw while loading').toEqual([]);
});

// The failure this exists for: a page that renders nothing looks exactly like an empty graph.
test('renders the graph', async ({ page }) => {
  const { nodes, edges, methods } = await counts(page);
  expect(nodes).toBe(39);
  expect(edges).toBe(26);
  expect(methods).toBe(5);
});

test('draws the canvas at a usable size', async ({ page }) => {
  const box = await page.locator('#graph canvas').first().boundingBox();
  expect(box.width).toBeGreaterThan(100);
  expect(box.height).toBeGreaterThan(100);
});
