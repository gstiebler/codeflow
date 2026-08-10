import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import type { Page } from '@playwright/test';
import type { Core } from 'cytoscape';

declare global {
  interface Window {
    /** What the Cytoscape page publishes for this suite to read. Nothing in that page uses it. */
    cy: Core;
    /** How many layouts the React Flow page has drawn. The only thing this suite asks that page. */
    drawn?: number;
  }
}

/**
 * The same questions, asked of each renderer in the way that renderer can answer them.
 *
 * One suite over two pages is the point of having two renderers: Cytoscape derives an edge's
 * visibility from its endpoints and a box's from its descendants, where React Flow is told both, so
 * an assertion that holds on one page and fails on the other is a fact about model.ts that neither
 * page could have produced alone.
 *
 * Both probes read what is *drawn*. The React Flow one goes through the DOM rather than through the
 * `view` object the page publishes, and the Cytoscape one reads `style('label')` rather than
 * `data('badge')`, for the same reason in both cases: a page whose data is right and whose rendering
 * is not would otherwise come out green.
 */
export type Probe = {
  name: string;
  page(fixture: string): string;
  /** Resolves once the page has drawn everything the current view says is visible. */
  settled(page: Page): Promise<void>;
  /** What the graph is painted onto, so that "did anything get drawn" has one answer per page. */
  surface: string;
  /** The drawn caption of every visible leaf - badges included, since a badge is drawn. */
  leafCaptions(page: Page): Promise<string[]>;
  boxLabels(page: Page): Promise<string[]>;
  tapLeaf(page: Page, label: string): Promise<void>;
  tapBox(page: Page, label: string): Promise<void>;
  /** Press R and let the page catch up. The one gesture that is not a click. */
  reset(page: Page): Promise<void>;
};

const at = (name: string) => pathToFileURL(resolve(`build/viewer-test/${name}.html`)).href;

/**
 * A caption carries the badge - `x ↓3` - and nearly every assertion names the plain label, so the
 * counts come off here rather than in each test. Splitting on the arrow is enough: no label holds
 * one.
 */
export const plain = (caption: string) => caption.split(' ↑')[0].split(' ↓')[0].trim();

/** Plain names, sorted: what most of the assertions compare against. */
export const labels = async (from: Promise<string[]>) => (await from).map(plain).sort();

/** Every drawn caption belonging to a leaf of this name - one per occurrence, badges included. */
export const captionsOf = async (from: Promise<string[]>, label: string) =>
  (await from).filter((caption) => plain(caption) === label).sort();

/**
 * Every gesture waits, and the waiting belongs here rather than in each test.
 *
 * React Flow draws nothing until ELK has answered, so a click changes the view at once and the
 * screen a layout later. Without this an assertion runs in the gap and reads the screen from
 * *before* the gesture - a race rather than a plain bug, since ELK answers in a few milliseconds on
 * these fixtures and most reads win it: the first run of this suite lost it exactly twice out of
 * fifteen, which is the worst way for a test to be wrong.
 *
 * The signal is the page's count of layouts drawn, and deliberately not "the DOM now matches the
 * page's own `view`". That would ask the renderer whether the renderer is right, and a page drawing
 * nodes it should have hidden would hang here instead of failing an assertion - which is exactly
 * what happened when that mutation was tried.
 */
const drewAgain = async (page: Page, gesture: () => Promise<void>) => {
  const before = await page.evaluate(() => window.drawn ?? 0);
  await gesture();
  await page.waitForFunction((was) => (window.drawn ?? 0) > was, before);
};

export const reactFlow: Probe = {
  name: 'react flow',
  page: (fixture) => at(fixture),
  // A node in the DOM, not a layout counted: the page counts its first layout before ELK has been
  // asked anything, so the counter cannot say whether the opening view is on screen yet.
  settled: async (page) => { await page.waitForSelector('.react-flow__node'); },
  surface: '.react-flow__pane',
  leafCaptions: (page) => page.evaluate(() =>
    [...document.querySelectorAll('.react-flow__node .cf-node')].map((n) => n.textContent ?? '')),
  boxLabels: (page) => page.evaluate(() =>
    [...document.querySelectorAll('.react-flow__node .cf-title')].map((n) => n.textContent ?? '')),
  // A real click, through React Flow's own hit testing, rather than a synthesised event: the click
  // landing on something else - an edge label, a child node drawn over the box - is exactly the kind
  // of wrongness a page-level test is here to catch.
  tapLeaf: (page, label) => drewAgain(page, async () => {
    await page.locator('.react-flow__node .cf-node')
      .filter({ hasText: new RegExp(`^${label}( [↑↓].*)*$`) }).first().click();
  }),
  tapBox: (page, label) => drewAgain(page, async () => {
    await page.locator('.react-flow__node .cf-title')
      .filter({ hasText: new RegExp(`^${label}$`) }).first().click();
  }),
  reset: (page) => drewAgain(page, () => page.keyboard.press('r')),
};

export const cytoscape: Probe = {
  name: 'cytoscape',
  page: (fixture) => at(`${fixture}-cytoscape`),
  // The type and not just presence: the browser publishes a global for every element id, so
  // `window.cy !== undefined` would be satisfied by a div and pass before the graph existed.
  // Cytoscape's own layout is fire-and-forget and moves nothing that is not already drawn, so
  // there is nothing further to wait for here - which is why this probe needs no view to compare
  // against and the React Flow one does.
  settled: async (page) => { await page.waitForFunction(() => typeof window.cy?.nodes === 'function'); },
  surface: '#graph canvas',
  leafCaptions: (page) => page.evaluate(() => window.cy.nodes()
    .filter((n) => n.data('type') !== 'METHOD' && n.visible()).map((n) => n.style('label') as string)),
  boxLabels: (page) => page.evaluate(() => window.cy.nodes('[type = "METHOD"]')
    .filter((n) => n.visible()).map((n) => n.style('label') as string)),
  // Emitted rather than clicked: a Cytoscape node is painted on a canvas and has no element to aim
  // at. The React Flow probe clicks for real, so between the two the click path is covered too.
  tapLeaf: async (page, label) => {
    await page.evaluate((l) => window.cy.nodes()
      .filter((n) => n.data('type') !== 'METHOD' && n.data('label') === l).emit('tap'), label);
  },
  tapBox: async (page, label) => {
    await page.evaluate((l) => window.cy.nodes('[type = "METHOD"]')
      .filter((n) => n.data('label') === l).emit('tap'), label);
  },
  reset: (page) => page.keyboard.press('r'),
};

export const RENDERERS = [reactFlow, cytoscape];
