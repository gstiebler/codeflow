import { test, expect, type Page } from '@playwright/test';
import { boxLabels, drewAgain, open } from './page.ts';

const NODE = '.react-flow__node';
const GROUP = '.react-flow__node .cf-group';
const LEAF = '.react-flow__node .cf-node';

/** A group's caption is `label (count)`; the label is what the assertions name. */
const groupLabels = (page: Page) => page.evaluate((selector) =>
  [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').split(' (')[0]).sort(), GROUP);

const button = (page: Page, name: string) => page.getByRole('button', { name, exact: true });

const condense = (page: Page) => drewAgain(page, () => button(page, 'Condensed').click());

const tapGroup = (page: Page, label: string) => drewAgain(page, async () => {
  await page.locator(GROUP).filter({ hasText: new RegExp(`^${label} \\(`) }).click();
});

const BOUNDARY_GROUPS = ['boundaryfunc1', 'boundaryfunc2', 'boundaryfunc3'];

/** The leaves drawn on their own, by their text. */
const leafLabels = (page: Page) => page.evaluate((selector) =>
  [...document.querySelectorAll(selector)].map((n) => (n.textContent ?? '').trim()).sort(), LEAF);

const EVENT = 'event entrypoint';

/**
 * The condensed view of `boundary`, asked of the DOM: three boundary methods called from five sites,
 * so it opens on five nodes - one per method, the entry point's event, and `program` - and opens up
 * where it is clicked.
 */
test.describe('boundary, condensed', () => {
  test.beforeEach(open('boundary'));

  test('the page opens in Boxes, and Condensed draws one node per boundary method, the event and the program', async ({ page }) => {
    await expect(button(page, 'Boxes')).toHaveAttribute('aria-pressed', 'true');
    expect((await boxLabels(page)).length).toBeGreaterThan(0);
    await expect(page.locator(GROUP)).toHaveCount(0);

    await condense(page);
    await expect(page.locator(NODE)).toHaveCount(5);
    expect(await groupLabels(page)).toEqual([...BOUNDARY_GROUPS, 'program']);
    expect(await leafLabels(page)).toEqual([EVENT]);
    expect(await boxLabels(page)).toEqual([]);
    await expect(button(page, 'Condensed')).toHaveAttribute('aria-pressed', 'true');
  });

  test('a boundary group has the boundaries purple outline, and the program does not', async ({ page }) => {
    await condense(page);
    const group = (label: string) => page.locator(GROUP).filter({ hasText: new RegExp(`^${label} \\(`) });
    await expect(group('boundaryfunc1')).toHaveCSS('border-top-color', 'rgb(123, 31, 162)');
    await expect(group('program')).not.toHaveCSS('border-top-color', 'rgb(123, 31, 162)');
  });

  test('clicking the program takes nodes out of it, and the boundary groups stay', async ({ page }) => {
    await condense(page);
    await expect(page.locator(LEAF)).toHaveCount(1);
    await tapGroup(page, 'program');
    expect(await page.locator(NODE).count()).toBeGreaterThan(5);
    expect(await page.locator(LEAF).count()).toBeGreaterThan(1);
    expect(await groupLabels(page)).toEqual(expect.arrayContaining(BOUNDARY_GROUPS));
  });

  test('hovering a leaf shows the method it was created in and its line', async ({ page }) => {
    await condense(page);
    await tapGroup(page, 'program');
    await expect(page.locator('.cf-tooltip')).toHaveCount(0);
    await page.locator(LEAF).first().hover();
    const tooltip = page.locator('.cf-tooltip');
    await expect(tooltip).toContainText('App#entrypoint');
    await expect(tooltip).toContainText('App.java:');
  });

  test('clicking the event takes what it starts out of their groups', async ({ page }) => {
    await condense(page);
    expect(await leafLabels(page)).toEqual([EVENT]);
    await drewAgain(page, () => page.locator(LEAF).filter({ hasText: EVENT }).click());
    expect(await page.locator(NODE).count()).toBeGreaterThan(5);
    expect(await page.locator(LEAF).count()).toBeGreaterThan(1);
    // The pair: the event is still drawn, and so is every boundary group.
    expect(await leafLabels(page)).toContain(EVENT);
    expect(await groupLabels(page)).toEqual(expect.arrayContaining(BOUNDARY_GROUPS));
  });

  test('Reset returns the condensed view to its opening', async ({ page }) => {
    await condense(page);
    await tapGroup(page, 'program');
    expect(await page.locator(NODE).count()).toBeGreaterThan(5);
    await drewAgain(page, () => button(page, 'Reset').click());
    await expect(page.locator(NODE)).toHaveCount(5);
    expect(await groupLabels(page)).toEqual([...BOUNDARY_GROUPS, 'program']);
    expect(await leafLabels(page)).toEqual([EVENT]);
  });
});
