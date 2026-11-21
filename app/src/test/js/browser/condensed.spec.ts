import { test, expect, type Page } from '@playwright/test';
import { drewAgain, open, tapGroup } from './page.ts';

const NODE = '.react-flow__node';
const GROUP = '.react-flow__node .cf-group';
const LEAF = '.react-flow__node .cf-node';

/** A group's caption is `label (count)`; the label is what the assertions name. */
const groupLabels = (page: Page) => page.evaluate((selector) =>
  [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').split(' (')[0]).sort(), GROUP);

const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true });

/** One per call site, so a method called twice is named twice. */
const BOUNDARY_GROUPS = ['boundaryfunc1', 'boundaryfunc2', 'boundaryfunc2', 'boundaryfunc3', 'boundaryfunc3'];
const OPENING = [...BOUNDARY_GROUPS, 'entrypoint'];

/** The leaves drawn on their own, by their text. */
const leafLabels = (page: Page) => page.evaluate((selector) =>
  [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').trim()).sort(), LEAF);

const EVENT = 'event entrypoint';

/**
 * The page on `boundary`, asked of the DOM: three boundary methods called from five sites, so it
 * opens on seven nodes - one box per call site (`entrypoint` itself and the five calls), and the
 * entry point's event - and opens up where it is clicked.
 */
test.describe('boundary', () => {
  test.beforeEach(open('boundary'));

  test('the page opens on one box per call site and the event', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(7);
    expect(await groupLabels(page)).toEqual(OPENING);
    expect(await leafLabels(page)).toEqual([EVENT]);
    // No method box: the one view has none.
    await expect(page.locator('.cf-box, .cf-title')).toHaveCount(0);
  });

  test('a boundary box has the boundaries purple outline, and a plain method box does not', async ({ page }) => {
    const group = (label: string) => page.locator(GROUP).filter({ hasText: new RegExp(`^${label} \\(`) });
    await expect(group('boundaryfunc1')).toHaveCSS('border-top-color', 'rgb(123, 31, 162)');
    await expect(group('entrypoint')).not.toHaveCSS('border-top-color', 'rgb(123, 31, 162)');
  });

  test('clicking the entry method box takes nodes out of it, and the boundary boxes stay', async ({ page }) => {
    await expect(page.locator(LEAF)).toHaveCount(1);
    await tapGroup(page, 'entrypoint');
    expect(await page.locator(NODE).count()).toBeGreaterThan(7);
    expect(await page.locator(LEAF).count()).toBeGreaterThan(1);
    expect(await groupLabels(page)).toEqual(expect.arrayContaining(BOUNDARY_GROUPS));
  });

  test('hovering a leaf shows the method it was created in and its line', async ({ page }) => {
    await tapGroup(page, 'entrypoint');
    // Off the drawing first: the pointer is still where `entrypoint` was clicked, and a line routed
    // through that spot after the move is hovered there, as it should be.
    await page.mouse.move(1, 1);
    await expect(page.locator('.cf-tooltip')).toHaveCount(0);
    await page.locator(LEAF).first().hover();
    const tooltip = page.locator('.cf-tooltip');
    await expect(tooltip).toContainText('App#entrypoint');
    await expect(tooltip).toContainText('App.java:');
  });

  test('clicking the event takes what it starts out of their groups', async ({ page }) => {
    expect(await leafLabels(page)).toEqual([EVENT]);
    await drewAgain(page, () => page.locator(LEAF).filter({ hasText: EVENT }).click());
    expect(await page.locator(NODE).count()).toBeGreaterThan(7);
    expect(await page.locator(LEAF).count()).toBeGreaterThan(1);
    // The pair: the event is still drawn, and so is every boundary group.
    expect(await leafLabels(page)).toContain(EVENT);
    expect(await groupLabels(page)).toEqual(expect.arrayContaining(BOUNDARY_GROUPS));
  });

  // The opening used to draw 13 lines - one per kind per pair, ten of them captioned with a kind -
  // packed into 40 px between layers. Each ordered pair is now one line, captioned only by a count.
  // Thirteen: one box per call site puts the calls in order, so the entry method's box is a hub the
  // five calls each draw a line to or from.
  test('the opening draws one line per ordered pair, captioned only with counts', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(7);
    const ids = await page.evaluate(() =>
      [...document.querySelectorAll('.react-flow__edge')].map((e) => e.getAttribute('aria-label')));
    expect(ids.length).toBe(13);
    expect(new Set(ids).size).toBe(ids.length);
    const captions = await page.locator('.react-flow__edge-text').allTextContents();
    // The pair: there are captions, and every one is a count.
    expect(captions.length).toBeGreaterThan(0);
    for (const caption of captions) expect(caption).toMatch(/^×\d+$/);
  });

  // entrypoint -> the first boundaryfunc2 call is two FLOWs and a TRUE: one grey, solid line whose hover names both kinds.
  test('a merged edge is neutral, and its hover shows the kinds it stands for with counts', async ({ page }) => {
    const edge = (from: string, to: string) => page.locator('.react-flow__edge')
      .filter({ has: page.locator(`xpath=self::*[@aria-label="Edge from ${from} to ${to}"]`) });
    const mixed = edge('b0', 'b16');
    await expect(mixed).toHaveCount(1);
    await expect(mixed.locator('.react-flow__edge-path')).toHaveCSS('stroke', 'rgb(153, 153, 153)');
    await expect(mixed.locator('.react-flow__edge-path')).toHaveCSS('stroke-dasharray', 'none');
    // The pair: a line of one kind keeps that kind's purple and dash.
    const order = edge('b16', 'b24');
    await expect(order.locator('.react-flow__edge-path')).toHaveCSS('stroke', 'rgb(123, 31, 162)');
    await expect(order.locator('.react-flow__edge-path')).not.toHaveCSS('stroke-dasharray', 'none');

    await expect(page.locator('.cf-tooltip')).toHaveCount(0);
    await mixed.locator('.react-flow__edge-textwrapper').hover();
    const lines = page.locator('.cf-tooltip > div');
    await expect(lines.first()).toHaveText('flow ×2, true ×1');
    await expect(page.locator('.cf-tooltip')).toContainText('Boundary#boundaryfunc2');
  });

  test('Reset returns the condensed view to its opening', async ({ page }) => {
    await tapGroup(page, 'entrypoint');
    expect(await page.locator(NODE).count()).toBeGreaterThan(7);
    await drewAgain(page, () => button(page, 'Reset').click());
    await expect(page.locator(NODE)).toHaveCount(7);
    expect(await groupLabels(page)).toEqual(OPENING);
    expect(await leafLabels(page)).toEqual([EVENT]);
  });
});
