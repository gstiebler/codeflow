import { test, expect } from '@playwright/test';
import { SURFACE, open, reset, tapLeaf } from './page.ts';

const NODE = '.react-flow__node';

/**
 * The page on `funcCall`, asked of the DOM rather than of the view: it draws, and R brings back the
 * opening - `program` and the entry point's event.
 */
test.describe('funcCall', () => {
  test.beforeEach(open('funcCall'));

  test('draws the graph at a usable size', async ({ page }) => {
    const box = await page.locator(SURFACE).first().boundingBox();
    expect(box!.width).toBeGreaterThan(100);
    expect(box!.height).toBeGreaterThan(100);
  });

  test('R returns to the opening set', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(2);
    await tapLeaf(page, 'event main');
    // Without this, the reset below would pass on a page where clicking never revealed anything.
    expect(await page.locator(NODE).count()).toBeGreaterThan(2);

    await reset(page);
    await expect(page.locator(NODE)).toHaveCount(2);
  });
});
