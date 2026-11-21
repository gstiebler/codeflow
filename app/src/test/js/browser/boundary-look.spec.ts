import { test, expect, type Page } from '@playwright/test';
import { leafCaptions, labels, open, tapGroup } from './page.ts';

/** The leaf drawn with this caption. */
const leaf = (page: Page, label: string) =>
  page.locator('.react-flow__node .cf-node').filter({ hasText: new RegExp(`^${label}( [↑↓].*)*$`) }).first();

const openSave = async (page: Page) => {
  await tapGroup(page, 'save');
  await tapGroup(page, 'save');
};

/**
 * How a boundary call and its order chain look on the page, asked of the DOM: the call's `call` in
 * `<strong>`, the token in the ORDER purple. `save` is a group at the opening; taking everything out of it
 * (two clicks) shows `x`, the token, `call save`.
 */
test.describe('boundaryRoots', () => {
  test.beforeEach(open('boundaryRoots'));

  test('a boundary call draws its lead in bold, and nothing else does', async ({ page }) => {
    await openSave(page);
    expect(await labels(leafCaptions(page))).toContain('call save');
    await expect(leaf(page, 'call save').locator('strong')).toHaveText('call');
    // As rendered, not just as text: a flex item of its own drops the space - `callsave`.
    await expect(leaf(page, 'call save')).toHaveJSProperty('innerText', 'call save');
    // The pair: the token and the parameter beside it are drawn with no bold part.
    await expect(leaf(page, 'boundaries first').locator('strong')).toHaveCount(0);
    await expect(leaf(page, 'x').locator('strong')).toHaveCount(0);
  });

  test('the order token is filled in the ORDER purple, and a variable is not', async ({ page }) => {
    await openSave(page);
    await expect(leaf(page, 'boundaries first')).toHaveCSS('background-color', 'rgba(123, 31, 162, 0.25)');
    await expect(leaf(page, 'x')).not.toHaveCSS('background-color', 'rgba(123, 31, 162, 0.25)');
  });
});
