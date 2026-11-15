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

/**
 * The condensed view of `boundary`, asked of the DOM: three boundary methods called from five sites,
 * so it opens on four nodes - one per method plus `program` - and opens up where it is clicked.
 */
test.describe('boundary, condensed', () => {
  test.beforeEach(open('boundary'));

  test('the page opens in Boxes, and Condensed draws one node per boundary method plus the program', async ({ page }) => {
    await expect(button(page, 'Boxes')).toHaveAttribute('aria-pressed', 'true');
    expect((await boxLabels(page)).length).toBeGreaterThan(0);
    await expect(page.locator(GROUP)).toHaveCount(0);

    await condense(page);
    await expect(page.locator(NODE)).toHaveCount(4);
    expect(await groupLabels(page)).toEqual([...BOUNDARY_GROUPS, 'program']);
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
    await expect(page.locator(LEAF)).toHaveCount(0);
    await tapGroup(page, 'program');
    expect(await page.locator(NODE).count()).toBeGreaterThan(4);
    expect(await page.locator(LEAF).count()).toBeGreaterThan(0);
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

  test('Reset returns the condensed view to its opening', async ({ page }) => {
    await condense(page);
    await tapGroup(page, 'program');
    expect(await page.locator(NODE).count()).toBeGreaterThan(4);
    await drewAgain(page, () => button(page, 'Reset').click());
    await expect(page.locator(NODE)).toHaveCount(4);
    expect(await groupLabels(page)).toEqual([...BOUNDARY_GROUPS, 'program']);
  });
});
