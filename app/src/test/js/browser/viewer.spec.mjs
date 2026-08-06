import { test, expect } from '@playwright/test';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

const PAGE = pathToFileURL(resolve('build/viewer-test/funcCall.html')).href;

test.beforeEach(async ({ page }) => {
  const errors = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(PAGE);
  // Checking the type, not just presence: `!== undefined` would be satisfied by anything at all,
  // and this guard is the only thing standing between a page that never initialised and a green run.
  await page.waitForFunction(() => typeof window.cy?.nodes === 'function');
  expect(errors, 'the page threw while loading').toEqual([]);
});

test('draws the canvas at a usable size', async ({ page }) => {
  const box = await page.locator('#graph canvas').first().boundingBox();
  expect(box.width).toBeGreaterThan(100);
  expect(box.height).toBeGreaterThan(100);
});

const leafLabels = (page) => page.evaluate(() => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.visible())
  .map((n) => n.data('label')).sort());

const boxLabels = (page) => page.evaluate(() => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.visible())
  .map((n) => n.data('label')).sort());

const tapLeaf = (page, label) => page.evaluate((l) => window.cy.nodes()
  .filter((n) => n.data('type') !== 'METHOD' && n.data('label') === l).emit('tap'), label);

const tapBox = (page, label) => page.evaluate((l) => window.cy.nodes('[type = "METHOD"]')
  .filter((n) => n.data('label') === l).emit('tap'), label);

const OPENING = ['5', '8', 'app', 'args', 'e', 'main', 'x', 'y'];

// Hiding, not removing. Under the old folding this read 11 - which is exactly the trap that made
// a node count comparable to the payload only after expanding everything first.
test('holds the whole payload however little is displayed', async ({ page }) => {
  const { nodes, edges } = await page.evaluate(() => ({
    nodes: window.cy.nodes().length,
    edges: window.cy.edges().length,
  }));
  expect(nodes).toBe(39);
  expect(edges).toBe(26);
});

test('opens showing the entry method body and nothing from a callee', async ({ page }) => {
  expect(await leafLabels(page)).toEqual(OPENING);
  // No callee has a visible node, so no callee box is drawn.
  expect(await boxLabels(page)).toEqual(['main']);
});

test('clicking a node reveals its neighbourhood three hops out', async ({ page }) => {
  await tapLeaf(page, 'x');
  const leaves = await leafLabels(page);
  // a, + , b and c are 1..3 hops from x and were all hidden a moment ago.
  expect(leaves).toContain('a');
  expect(leaves).toContain('+');
  expect(leaves).toContain('b');
  expect(leaves).toContain('c');
  // methodA's return node is exactly 4 hops out. This is what fails if the bound is off by one,
  // and it only means anything next to the four assertions above.
  expect(leaves).not.toContain('methodA');
  // The box appears because it now contains something, never because we showed it.
  expect(await boxLabels(page)).toEqual(['main', 'methodA']);
});

test('reveals accumulate across clicks', async ({ page }) => {
  await tapLeaf(page, 'x');
  const afterX = await leafLabels(page);
  await tapLeaf(page, 'e');
  const afterE = await leafLabels(page);

  // Nothing the first click revealed may vanish on the second.
  for (const label of afterX) expect(afterE).toContain(label);
  // And the second click has to actually add something, or the loop above proves nothing.
  expect(afterE.length).toBeGreaterThan(afterX.length);
  expect(afterE).toContain('d');
});

test('folding a method box hides its contents, nested boxes and all', async ({ page }) => {
  await tapLeaf(page, 'e');
  // methodC's return node sits two levels down, inside methodB's methodC box.
  expect(await boxLabels(page)).toEqual(['main', 'methodB', 'methodC']);

  await tapBox(page, 'methodB');
  // Both go. If the fold used children() instead of descendants(), methodC would survive.
  expect(await boxLabels(page)).toEqual(['main']);
  expect(await leafLabels(page)).toEqual(OPENING);
});

test('R returns to the opening set', async ({ page }) => {
  await tapLeaf(page, 'x');
  await tapLeaf(page, 'e');
  // Without this, the reset below would pass on a page where clicking never revealed anything.
  expect((await leafLabels(page)).length).toBeGreaterThan(OPENING.length);

  await page.keyboard.press('r');
  expect(await leafLabels(page)).toEqual(OPENING);
});
