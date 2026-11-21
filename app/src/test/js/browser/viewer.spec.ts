import { test, expect } from '@playwright/test';
import { SURFACE, open, reset, tapLeafAll } from './page.ts';

const NODE = '.react-flow__node';

/**
 * The page on `funcCall`, asked of the DOM rather than of the view: it draws, and R brings back the
 * opening - the `program` box, the entry method's box, and the entry point's event, parameters and return.
 */
test.describe('funcCall', () => {
  test.beforeEach(open('funcCall'));

  test('draws the graph at a usable size', async ({ page }) => {
    const box = await page.locator(SURFACE).first().boundingBox();
    expect(box!.width).toBeGreaterThan(100);
    expect(box!.height).toBeGreaterThan(100);
  });

  test('R returns to the opening set', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(5);
    await expect(page.locator('.react-flow__node .cf-group').filter({ hasText: /^program \(28\)/ })).toHaveCount(1);
    await tapLeafAll(page, 'event main');
    // Without this, the reset below would pass on a page where clicking never revealed anything.
    await expect(page.locator(NODE)).toHaveCount(18);

    await reset(page);
    await expect(page.locator(NODE)).toHaveCount(5);
  });
});
