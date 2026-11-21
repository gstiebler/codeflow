import { test, expect, type Page } from '@playwright/test';
import { drewAgain, open } from './page.ts';

const LANE = '.react-flow__node .cf-lane';
const leafNode = (page: Page, label: string) =>
  page.locator('.react-flow__node').filter({ has: page.locator('.cf-node', { hasText: new RegExp(`^${label}$`) }) });
const tap = (page: Page, label: string) => drewAgain(page, () => leafNode(page, label).click());

/**
 * A condensed click on `boundary`, asked of the DOM: `c` flows from the event into two `z`s and on
 * into boundaryfunc3, and `b` is on none of that - so `c`'s path is laid out in a lane, `b` faded.
 */
test.describe('boundary, focus', () => {
  test.beforeEach(open('boundary'));

  test('a click lays its path out in a lane and fades the rest; Esc puts it back', async ({ page }) => {
    await tap(page, 'event entrypoint');
    await tap(page, 'c');
    await expect(page.locator(LANE)).toHaveCount(1);
    await expect(leafNode(page, 'b')).toHaveClass(/cf-dim/);
    // The pair: the clicked node and its source are on the path, not faded.
    await expect(leafNode(page, 'c')).not.toHaveClass(/cf-dim/);
    await expect(leafNode(page, 'event entrypoint')).not.toHaveClass(/cf-dim/);

    await drewAgain(page, () => page.keyboard.press('Escape'));
    await expect(page.locator(LANE)).toHaveCount(0);
    await expect(leafNode(page, 'b')).not.toHaveClass(/cf-dim/);
    await expect(leafNode(page, 'b')).toHaveCount(1);
  });

  test('a click on the canvas clears the lane too', async ({ page }) => {
    await tap(page, 'event entrypoint');
    await expect(page.locator(LANE)).toHaveCount(1);
    await drewAgain(page, () => page.locator('.react-flow__pane').click({ position: { x: 5, y: 300 } }));
    await expect(page.locator(LANE)).toHaveCount(0);
    await expect(leafNode(page, 'event entrypoint')).toHaveCount(1);
  });
});
