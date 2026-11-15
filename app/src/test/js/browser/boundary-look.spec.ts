import { test, expect, type Page } from '@playwright/test';
import { leafCaptions, labels, open, tapBox } from './page.ts';

/** The leaf drawn with this caption, badges ignored. */
const leaf = (page: Page, label: string) =>
  page.locator('.react-flow__node .cf-node').filter({ hasText: new RegExp(`^${label}( [↑↓].*)*$`) }).first();

/**
 * How a boundary call and its order chain look on the page, asked of the DOM: the call's `call` in
 * `<strong>`, the token in the ORDER purple. `save` is a closed box at the opening; opening it shows
 * its assumed body - `x`, the token, `call save`, `result`.
 */
test.describe('boundaryRoots', () => {
  test.beforeEach(open('boundaryRoots'));

  test('a boundary call draws its lead in bold, and nothing else does', async ({ page }) => {
    await tapBox(page, 'save');
    expect(await labels(leafCaptions(page))).toContain('call save');
    await expect(leaf(page, 'call save').locator('strong')).toHaveText('call');
    // The pair: the token and the result beside it are drawn with no bold part.
    await expect(leaf(page, 'boundaries first').locator('strong')).toHaveCount(0);
    await expect(leaf(page, 'result').locator('strong')).toHaveCount(0);
  });

  test('the order token is filled in the ORDER purple, and a variable is not', async ({ page }) => {
    await tapBox(page, 'save');
    await expect(leaf(page, 'boundaries first')).toHaveCSS('background-color', 'rgba(123, 31, 162, 0.25)');
    await expect(leaf(page, 'result')).not.toHaveCSS('background-color', 'rgba(123, 31, 162, 0.25)');
  });
});
