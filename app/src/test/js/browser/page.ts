import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { expect, type Page } from '@playwright/test';

declare global {
  interface Window {
    /** How many layouts the page has drawn. The only thing this suite asks the page directly. */
    drawn?: number;
  }
}

/**
 * How the browser suite talks to the page: open it, read what is drawn, click, wait.
 *
 * Everything here goes through the DOM rather than through the `view` object the page publishes. A
 * page whose data is right and whose rendering is not would otherwise come out green, and the
 * rendering is the whole of what a page test can add over a unit test on model.ts.
 */

const at = (name: string) => pathToFileURL(resolve(`build/viewer-test/${name}.html`)).href;

/** Open a fixture's page and fail if it threw on the way up. */
export const open = (fixture: string) => async ({ page }: { page: Page }) => {
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  await page.goto(at(fixture));
  // A node in the DOM, not a layout counted: the page counts its first layout before ELK has been
  // asked anything, so the counter cannot say whether the opening view is on screen yet.
  await page.waitForSelector('.react-flow__node');
  expect(errors, 'the page threw while loading').toEqual([]);
};

/** What the graph is painted onto, so "did anything get drawn" has an answer. */
export const SURFACE = '.react-flow__pane';

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

/** The drawn caption of every visible leaf - badges included, since a badge is drawn. */
export const leafCaptions = (page: Page) => page.evaluate(() =>
  [...document.querySelectorAll('.react-flow__node .cf-node')].map((n) => n.textContent ?? ''));

export const boxLabels = (page: Page) => page.evaluate(() =>
  [...document.querySelectorAll('.react-flow__node .cf-title')].map((n) => n.textContent ?? ''));

/**
 * Every gesture waits, and the waiting belongs here rather than in each test.
 *
 * The page draws nothing until ELK has answered, so a click changes the view at once and the screen
 * a layout later. Without this an assertion runs in the gap and reads the screen from *before* the
 * gesture - a race rather than a plain bug, since ELK answers in a few milliseconds on these
 * fixtures and most reads win it: the first run of this suite lost it exactly twice out of fifteen,
 * which is the worst way for a test to be wrong.
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

// A real click, through React Flow's own hit testing, rather than a synthesised event: the click
// landing on something else - an edge label, a child node drawn over the box - is exactly the kind
// of wrongness a page-level test is here to catch.
export const tapLeaf = (page: Page, label: string) => drewAgain(page, async () => {
  await page.locator('.react-flow__node .cf-node')
    .filter({ hasText: new RegExp(`^${label}( [↑↓].*)*$`) }).first().click();
});

export const tapBox = (page: Page, label: string) => drewAgain(page, async () => {
  await page.locator('.react-flow__node .cf-title')
    .filter({ hasText: new RegExp(`^${label}$`) }).first().click();
});

/** Press R and let the page catch up. The one gesture that is not a click. */
export const reset = (page: Page) => drewAgain(page, () => page.keyboard.press('r'));
