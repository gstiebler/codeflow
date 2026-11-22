import { test, expect, type Page } from '@playwright/test';
import { drewAgain, frameOf, open, PICKER, tapGroupAll, tapLeafAll } from './page.ts';

/** A drawn node: a frame is the outline around some, not one of them. */
const NODE = '.react-flow__node:not(.react-flow__node-condensedFrame)';
const GROUP = '.react-flow__node .cf-group';
const LEAF = '.react-flow__node .cf-node';

/** A group's caption is `label (count)`; the label is what the assertions name. */
const groupLabels = (page: Page) => page.evaluate((selector) =>
  [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').split(' (')[0]).sort(), GROUP);

const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true });

/** One per call site, so a method called twice is named twice. */
const BOUNDARY_GROUPS = ['boundaryfunc1', 'boundaryfunc2', 'boundaryfunc2', 'boundaryfunc3', 'boundaryfunc3'];
/** The entry box is a frame at the opening: its rest node reads `13 more`, and `program` holds everything else. */
const OPENING = ['13 more', 'program'];

/** The leaves drawn on their own, by their text. */
const leafLabels = (page: Page) => page.evaluate((selector) =>
  [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').trim()).sort(), LEAF);

const EVENT = 'event entrypoint';
/** The leaves the opening draws on their own: the event and the entry's return. */
const OPENING_LEAVES = ['entrypoint', EVENT];

/**
 * The page on `boundary`, asked of the DOM: three boundary methods called from five sites, directly
 * by the entry method, so it opens on four nodes - `entrypoint` (which still hides its thirteen other
 * members, as `13 more`), `program` with the rest, and on their own the event and the entry's return -
 * and no boundary box: they open only when asked for, and it opens up where it is clicked.
 */
test.describe('boundary', () => {
  test.beforeEach(open('boundary'));

  test('the page opens on the entry box, the event, the return and program, and no boundary box', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(4);
    expect(await groupLabels(page)).toEqual(OPENING);
    expect(await leafLabels(page)).toEqual(OPENING_LEAVES);
    // No method box: the one view has none, and no boundary box is drawn.
    await expect(page.locator('.cf-box, .cf-title')).toHaveCount(0);
    await expect(page.locator(GROUP).filter({ hasText: /boundaryfunc/ })).toHaveCount(0);
  });

  test('a boundary box has the boundaries purple outline, and a plain method box does not', async ({ page }) => {
    await tapGroupAll(page, 'entrypoint');
    const group = (label: string) => page.locator(GROUP).filter({ hasText: new RegExp(`^${label} \\(`) });
    await expect(group('boundaryfunc1')).toHaveCSS('border-top-color', 'rgb(123, 31, 162)');
    // The entry method is drawn as a frame, which is not a boundary frame.
    await expect(frameOf(page, 'entrypoint').locator('.cf-frame')).not.toHaveCSS('border-top-color', 'rgb(123, 31, 162)');
  });

  test('clicking the entry method box takes nodes out of it, and the boundary boxes it called open', async ({ page }) => {
    await expect(page.locator(LEAF)).toHaveCount(2);
    await tapGroupAll(page, 'entrypoint');
    await expect(page.locator(NODE)).toHaveCount(17);
    await expect(page.locator(LEAF)).toHaveCount(11);
    // `program` held nothing but their members, so it is gone.
    expect(await groupLabels(page)).toEqual(['4 more', ...BOUNDARY_GROUPS]);
  });

  test('hovering a leaf shows the method it was created in and its line', async ({ page }) => {
    await tapGroupAll(page, 'entrypoint');
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
    expect(await leafLabels(page)).toEqual(OPENING_LEAVES);
    // Five things to show, so it asks.
    await tapLeafAll(page, EVENT);
    await expect(page.locator(NODE)).toHaveCount(10);
    await expect(page.locator(LEAF)).toHaveCount(7);
    // The pair: the event is still drawn; boundaryfunc1 is now a frame (`4 more`), the entry's rest node reads `9 more`, and program is what is left.
    expect(await leafLabels(page)).toContain(EVENT);
    expect(await groupLabels(page)).toEqual(['4 more', '9 more', 'program']);
  });

  // The event's line into the entry stands for four things: three parameters and a value, so it asks, parameters first.
  test('clicking the event\'s line into the entry lists the parameters, then the values', async ({ page }) => {
    await page.locator('.react-flow__edge[aria-label="Edge from n1 to b0"] .react-flow__edge-textwrapper').click();
    const popup = page.locator(PICKER);
    await expect(popup.locator('h4')).toHaveText(['parameters', 'values']);
    await expect(popup.getByRole('checkbox')).toHaveCount(4);
    expect(await popup.getByRole('checkbox').evaluateAll((boxes) => boxes.map((b) => b.getAttribute('aria-label')))).toEqual(['a', 'b', 'c', '0']);
    await expect(page.locator(LEAF)).toHaveCount(2);
  });

  // The opening used to draw 26 lines, one per ordered pair among the entry's leaves and the five boundary
  // boxes. Four: the event to the return and to the entry's rest, the rest and `program` both ways. Each
  // ordered pair is one line, captioned only by a count.
  test('the opening draws one line per ordered pair, captioned only with counts', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(4);
    const ids = await page.evaluate(() =>
      [...document.querySelectorAll('.react-flow__edge')].map((e) => e.getAttribute('aria-label')));
    expect(ids.length).toBe(4);
    expect(new Set(ids).size).toBe(ids.length);
    // The event's edges go to its return and to the entry's `13 more`, never into `program`.
    expect(ids.filter((id) => id!.startsWith('Edge from n1 to ')).sort()).toEqual(['Edge from n1 to b0', 'Edge from n1 to n2']);
    const captions = await page.locator('.react-flow__edge-text').allTextContents();
    // The pair: there are captions, and every one is a count; the event's edge to its return has none.
    expect(captions.sort()).toEqual(['×13', '×4', '×5']);
  });

  // entrypoint's rest -> program is eleven FLOWs, a TRUE and a FALSE: one grey, solid line whose hover names the kinds.
  test('a merged edge is neutral, and its hover shows the kinds it stands for with counts', async ({ page }) => {
    const edge = (from: string, to: string) => page.locator('.react-flow__edge')
      .filter({ has: page.locator(`xpath=self::*[@aria-label="Edge from ${from} to ${to}"]`) });
    const mixed = edge('b0', 'program');
    await expect(mixed).toHaveCount(1);
    await expect(mixed.locator('.react-flow__edge-path')).toHaveCSS('stroke', 'rgb(153, 153, 153)');
    await expect(mixed.locator('.react-flow__edge-path')).toHaveCSS('stroke-dasharray', 'none');
    // The pair: a line of one kind keeps that kind's teal and dash.
    const event = edge('n1', 'b0');
    await expect(event.locator('.react-flow__edge-path')).toHaveCSS('stroke', 'rgb(0, 137, 123)');
    await expect(event.locator('.react-flow__edge-path')).not.toHaveCSS('stroke-dasharray', 'none');

    await expect(page.locator('.cf-tooltip')).toHaveCount(0);
    await mixed.locator('.react-flow__edge-textwrapper').hover();
    const lines = page.locator('.cf-tooltip > div');
    await expect(lines.first()).toHaveText('flow ×11, true ×1, false ×1');
    await expect(page.locator('.cf-tooltip')).toContainText('Boundary#boundaryfunc2');
  });

  // The tooltip names an edge only by hover; the line must say which one it is about.
  test('the edge under the pointer is drawn thicker, and only that one', async ({ page }) => {
    const paths = page.locator('.react-flow__edge .react-flow__edge-path');
    expect(await paths.count()).toBeGreaterThanOrEqual(2);
    const widths = () => paths.evaluateAll((ps) => ps.map((p) => getComputedStyle(p).strokeWidth));
    const rest = (n: number) => Array.from({ length: n }, () => '1.5px');
    await page.mouse.move(1, 1);
    expect(await widths()).toEqual(rest(await paths.count()));

    // A point a third along the first edge, in page pixels, so the real mouse lands on it.
    const at = await paths.first().evaluate((p: SVGPathElement) => {
      const m = p.getScreenCTM()!;
      const pt = p.getPointAtLength(p.getTotalLength() / 3).matrixTransform(m);
      return { x: pt.x, y: pt.y };
    });
    await page.mouse.move(at.x, at.y);
    const n = await paths.count();
    expect(await widths()).toEqual(['3px', ...rest(n - 1)]);
    // A captioned edge's caption goes bold while it is hovered, and only then.
    const caption = page.locator('.react-flow__edge-text').first();
    await page.mouse.move(1, 1);
    await expect(caption).toHaveCSS('font-weight', '400');
    await page.locator('.react-flow__edge-textwrapper').first().hover();
    await expect(caption).toHaveCSS('font-weight', '700');

    await page.mouse.move(1, 1);
    expect(await widths()).toEqual(rest(n));
  });

  test('Reset returns the condensed view to its opening', async ({ page }) => {
    await tapGroupAll(page, 'entrypoint');
    await expect(page.locator(NODE)).toHaveCount(17);
    await drewAgain(page, () => button(page, 'Reset').click());
    await expect(page.locator(NODE)).toHaveCount(4);
    expect(await groupLabels(page)).toEqual(OPENING);
    expect(await leafLabels(page)).toEqual(OPENING_LEAVES);
  });
});

/**
 * `boundaryBranch` has fourteen boundary call sites. The opening draws none of them and has two lines out of
 * the event - to its return and to the entry's `3 more`, none into `program`; a click on the second asks
 * what the event starts, the parameters first.
 */
test.describe('boundaryBranch, the opening', () => {
  test.beforeEach(open('boundaryBranch'));

  test('four nodes, three lines, no boundary box, and no line from the event into program', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(4);
    expect(await groupLabels(page)).toEqual(['3 more', 'program']);
    expect(await leafLabels(page)).toEqual(['entrypoint', 'event entrypoint']);
    await expect(page.locator('.react-flow__edge')).toHaveCount(3);
    const eventLines = await page.evaluate(() => [...document.querySelectorAll('.react-flow__edge')]
      .map((e) => e.getAttribute('aria-label')!).filter((l) => l.startsWith('Edge from n1 to ')).sort());
    expect(eventLines).toEqual(['Edge from n1 to b0', 'Edge from n1 to n2']);
  });

  // `a`, `b` and `c` are all the event's line stands for here: three, so no popup.
  test('clicking the event\'s line into the entry shows its three parameters, with no popup', async ({ page }) => {
    await drewAgain(page, () => page.locator('.react-flow__edge[aria-label="Edge from n1 to b0"] .react-flow__edge-textwrapper').click());
    await expect(page.locator(PICKER)).toHaveCount(0);
    expect(await leafLabels(page)).toEqual(['a', 'b', 'c', 'entrypoint', 'event entrypoint']);
    // The parameters flow into four plain boxes, which open; the fourteen boundary boxes do not.
    await expect(page.locator(NODE)).toHaveCount(10);
    expect(await groupLabels(page)).toEqual(['nested', 'noElse', 'onlyElse', 'program', 'untouched']);
  });
});
