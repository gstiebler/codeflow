import { test, expect, type Page } from '@playwright/test';
import { drewAgain, groupNode, open, PICKER, tapLeaf } from './page.ts';

/** A drawn node: a frame is the outline around some, not one of them. */
const NODE = '.react-flow__node:not(.react-flow__node-condensedFrame)';
const GROUP = '.react-flow__node .cf-group';
const LEAF = '.react-flow__node .cf-node';
const POPUP = PICKER;

/** The leaves drawn on their own, by their text. */
const leafLabels = (page: Page) => page.evaluate((selector) =>
  [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').trim()).sort(), LEAF);

/** What the event's line shows: the parameters and a value, plus the event and the return. */
const START_LEAVES = ['0', 'a', 'b', 'c', 'entrypoint', 'event entrypoint'];

/** Click the line from the event into the entry's rest node, which stands for four things, and show them all. */
const showWhatTheEventStarts = async (page: Page) => {
  await drewAgain(page, async () => {
    await page.locator('.react-flow__edge[aria-label="Edge from n1 to b0"] .react-flow__edge-textwrapper').click();
    await page.locator(POPUP).getByRole('button', { name: 'Show all', exact: true }).click();
  });
};

/**
 * On `boundary`, after the event's line is clicked: `a` flows into five values (`x`, `>`, `y`, `x`, `y`)
 * and `b` into three, so a click on `a` asks which to show and a click on `b` shows them. Counts are what
 * the page draws, read off it.
 */
test.describe('the picker', () => {
  test.beforeEach(async ({ page }) => {
    await open('boundary')({ page });
    await showWhatTheEventStarts(page);
  });

  test('a click that could show more than three opens the popup, listing them under their section', async ({ page }) => {
    await page.locator(LEAF).filter({ hasText: /^a$/ }).click();
    const popup = page.locator(POPUP);
    await expect(popup).toBeVisible();
    await expect(popup.locator('b')).toHaveText('a');
    await expect(popup.locator('h4')).toHaveText(['goes to']);
    await expect(popup.getByRole('checkbox')).toHaveCount(5);
    await expect(popup.getByRole('checkbox', { checked: true })).toHaveCount(0);
    await expect(popup.getByRole('button', { name: 'Show selected (0)' })).toBeDisabled();
    // Nothing was shown yet, and five rows is too few for a search box.
    await expect(page.locator(LEAF)).toHaveCount(6);
    await expect(popup.getByRole('searchbox')).toHaveCount(0);
  });

  test('ticking one and Show selected adds exactly that', async ({ page }) => {
    await page.locator(LEAF).filter({ hasText: /^a$/ }).click();
    const popup = page.locator(POPUP);
    await popup.getByRole('checkbox', { name: '>' }).check();
    await expect(popup.getByRole('button', { name: 'Show selected (1)' })).toBeEnabled();
    await drewAgain(page, () => popup.getByRole('button', { name: 'Show selected (1)' }).click());
    await expect(popup).toHaveCount(0);
    await expect(page.locator(LEAF)).toHaveCount(7);
    await expect(page.locator(GROUP)).toHaveCount(2);
    expect(await leafLabels(page)).toEqual(['>', ...START_LEAVES].sort());
  });

  test('Show all adds everything the click could show', async ({ page }) => {
    await page.locator(LEAF).filter({ hasText: /^a$/ }).click();
    await drewAgain(page, () => page.locator(POPUP).getByRole('button', { name: 'Show all', exact: true }).click());
    await expect(page.locator(POPUP)).toHaveCount(0);
    await expect(page.locator(LEAF)).toHaveCount(11);
    await expect(page.locator(GROUP)).toHaveCount(6);
    expect(await leafLabels(page)).toEqual([...START_LEAVES, '>', 'x', 'x', 'y', 'y'].sort());
  });

  test('a click that could show three or fewer shows them with no popup', async ({ page }) => {
    await tapLeaf(page, 'b');
    await expect(page.locator(LEAF)).toHaveCount(9);
    await expect(page.locator(GROUP)).toHaveCount(4);
    expect(await leafLabels(page)).toEqual([...START_LEAVES, '>', 'y', 'y'].sort());
    await expect(page.locator(POPUP)).toHaveCount(0);
  });

  test('Escape, the x and a click on the pane close it without showing anything', async ({ page }) => {
    const a = page.locator(LEAF).filter({ hasText: /^a$/ });
    await a.click();
    await expect(page.locator(POPUP)).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator(POPUP)).toHaveCount(0);

    await a.click();
    await expect(page.locator(POPUP)).toBeVisible();
    await page.locator(POPUP).getByRole('button', { name: 'Close' }).click();
    await expect(page.locator(POPUP)).toHaveCount(0);

    await a.click();
    await expect(page.locator(POPUP)).toBeVisible();
    await page.locator('.react-flow__pane').click({ position: { x: 5, y: 300 } });
    await expect(page.locator(POPUP)).toHaveCount(0);
    // The pair: nothing was shown by any of the three.
    await expect(page.locator(LEAF)).toHaveCount(6);
    await expect(page.locator(NODE)).toHaveCount(8);
  });

  test('the popup opens inside the window', async ({ page }) => {
    await page.setViewportSize({ width: 500, height: 420 });
    await page.locator(LEAF).filter({ hasText: /^a$/ }).click();
    const box = (await page.locator(POPUP).boundingBox())!;
    expect(box.x).toBeGreaterThanOrEqual(0);
    expect(box.y).toBeGreaterThanOrEqual(0);
    expect(box.x + box.width).toBeLessThanOrEqual(500);
    expect(box.y + box.height).toBeLessThanOrEqual(420);
  });
});

/**
 * On `boundaryBranch`, after the event's line is clicked (it shows `a`, `b`, `c` and opens four plain boxes),
 * clicking the `onlyElse` box offers nine things: the boxes it called (`open`, `send`, `close`) under `calls`,
 * and six of its values.
 */
test.describe('the picker on a box', () => {
  test.beforeEach(async ({ page }) => {
    await open('boundaryBranch')({ page });
    await drewAgain(page, () => page.locator('.react-flow__edge[aria-label="Edge from n1 to b0"] .react-flow__edge-textwrapper').click());
  });

  const onlyElse = (page: Page) => groupNode(page, 'onlyElse');

  test('lists calls then values, with no filter box under eleven rows', async ({ page }) => {
    await (await onlyElse(page)).click();
    const popup = page.locator(POPUP);
    await expect(popup.locator('h4')).toHaveText(['calls', 'values']);
    await expect(popup.getByRole('checkbox')).toHaveCount(9);
    await expect(popup.getByRole('searchbox')).toHaveCount(0);
  });

  test('past ten rows it has a filter box, by label without regard to case', async ({ page }) => {
    // `nested` offers fifteen things: five calls and ten values.
    await (await groupNode(page, 'nested')).click();
    const popup = page.locator(POPUP);
    await expect(popup.getByRole('checkbox')).toHaveCount(15);
    await popup.getByRole('searchbox').fill('OPE');
    await expect(popup.getByRole('checkbox')).toHaveCount(1);
    await expect(popup.getByRole('checkbox', { name: 'open' })).toBeVisible();
    await expect(popup.locator('h4')).toHaveText(['calls']);
  });

  test('picking a call opens that box as a group and draws no leaf for it', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(10);
    await (await onlyElse(page)).click();
    const popup = page.locator(POPUP);
    await popup.getByRole('checkbox', { name: 'open' }).check();
    await drewAgain(page, () => popup.getByRole('button', { name: 'Show selected (1)' }).click());
    await expect(page.locator(NODE)).toHaveCount(11);
    await expect(page.locator(LEAF)).toHaveCount(5);
    await expect(page.locator('.react-flow__node .cf-group').filter({ hasText: /^open \(/ })).toHaveCount(1);
  });

  test('picking a value draws just that leaf', async ({ page }) => {
    await (await onlyElse(page)).click();
    const popup = page.locator(POPUP);
    await popup.getByRole('checkbox', { name: 'boundaries entrypoint' }).check();
    await drewAgain(page, () => popup.getByRole('button', { name: 'Show selected (1)' }).click());
    await expect(page.locator(NODE)).toHaveCount(11);
    await expect(page.locator(LEAF)).toHaveCount(6);
  });

  test('typing r in the filter does not reset the page', async ({ page }) => {
    await (await groupNode(page, 'nested')).click();
    await page.locator(POPUP).getByRole('searchbox').fill('r');
    await expect(page.locator(POPUP)).toBeVisible();
    await expect(page.locator(POPUP).getByRole('searchbox')).toHaveValue('r');
  });
});
