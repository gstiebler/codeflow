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
//
// Counted with everything expanded, because folding a block removes its children from the graph
// rather than hiding them - so the totals are only comparable to the payload once nothing is
// folded. 39 nodes and 26 edges is what --json and --graphml report for this fixture.
test('renders the whole graph once everything is expanded', async ({ page }) => {
  await page.evaluate(() => window.api.expandAll());
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

const methodState = (page) => page.evaluate(() => {
  const methods = window.cy.nodes('[type = "METHOD"]');
  return {
    present: methods.map((n) => n.data('label')).sort(),
    // isExpandable means folded and openable; isCollapsible means open and foldable.
    folded: methods.filter((n) => window.api.isExpandable(n)).map((n) => n.data('label')).sort(),
    open: methods.filter((n) => window.api.isCollapsible(n)).map((n) => n.data('label')).sort(),
  };
});

test('opens with only the outermost method expanded', async ({ page }) => {
  const { present, folded, open } = await methodState(page);
  // main is open, so both of its calls are in the graph and both are folded. The two methodC
  // blocks are inside the folded methodB and so are not in the graph at all yet - which is the
  // point: the cost of a deep call tree is not paid until it is asked for.
  expect(present).toEqual(['main', 'methodA', 'methodB']);
  expect(open).toEqual(['main']);
  expect(folded).toEqual(['methodA', 'methodB']);
});

test('expanding a method reveals the calls nested inside it', async ({ page }) => {
  await page.evaluate(() => {
    const methodB = window.cy.nodes('[type = "METHOD"]').filter((n) => n.data('label') === 'methodB');
    window.api.expand(methodB);
  });
  const { present, folded } = await methodState(page);
  // methodC is inlined at both of methodB's call sites, so opening methodB brings in two of them,
  // each folded in turn.
  expect(present).toEqual(['main', 'methodA', 'methodB', 'methodC', 'methodC']);
  expect(folded).toEqual(['methodA', 'methodC', 'methodC']);
});

/**
 * A collapsed block's edges become meta-edges, which say only that *something* inside connects to
 * the other end. Drawn like a real edge, that asserts a flow between two nodes that never touched.
 */
test('draws meta-edges differently from real ones', async ({ page }) => {
  const styles = await page.evaluate(() => {
    const meta = window.cy.edges('.cy-expand-collapse-meta-edge');
    const real = window.cy.edges().not(meta);
    return {
      metaCount: meta.length,
      metaStyle: meta.length ? meta[0].renderedStyle('line-style') : null,
      realStyle: real.length ? real[0].renderedStyle('line-style') : null,
    };
  });
  expect(styles.metaCount).toBeGreaterThan(0);
  expect(styles.metaStyle).toBe('dashed');
  expect(styles.realStyle).toBe('solid');
});
