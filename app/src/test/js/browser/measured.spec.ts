import { test, expect, type Page } from '@playwright/test';
import { drewAgain, open, PICKER } from './page.ts';

const BUTTON = { name: 'Boundaries', exact: true };
const ROWS = '[aria-label="Boundaries"] .cf-boundary-row:not(:disabled)';

/**
 * React Flow drops a node's measurement whenever it is handed a new node object, and the page hands
 * it new ones every animation frame. A node it measured and then lost before re-rendering was never
 * measured again: `visibility: hidden` for good, and no edge, since edges need the node's handles.
 *
 * So: animation on (never `cfAnimationMs = 0` here), on a page whose frames are slow enough to lose
 * that race - `bigBoundary`, see global-setup.mjs - and after each drawing no node hidden and every
 * edge there. Asked of the DOM, as the rest of this suite is.
 */
const hidden = (page: Page) => page.evaluate(() =>
  [...document.querySelectorAll<HTMLElement>('.react-flow__node')].filter((n) => n.style.visibility === 'hidden').length);

/** React Flow draws one `.react-flow__edge` per edge it has the ends of; one it cannot place is not in the DOM. */
const edges = (page: Page) => page.locator('.react-flow__edge').count();

test.describe('bigBoundary, animated redraws', () => {
  test.beforeEach(open('bigBoundary'));

  test('every Boundaries row click leaves all nodes measured and all edges drawn', async ({ page, browser }) => {
    const clicks = 4;
    // The reference: the same clicks with no animation, where nothing is redrawn between frames.
    const still = await browser.newPage();
    await still.addInitScript(() => { window.cfAnimationMs = 0; });
    await open('bigBoundary')({ page: still });
    await still.getByRole('button', BUTTON).click();
    const reference: number[] = [];
    for (let i = 0; i < clicks; i++) {
      await drewAgain(still, () => still.locator(ROWS).first().click());
      await still.waitForTimeout(400);
      reference.push(await edges(still));
    }
    await still.close();
    expect(reference.every((n) => n > 0), 'the reference page draws edges').toBe(true);

    await page.getByRole('button', BUTTON).click();
    for (let i = 0; i < clicks; i++) {
      await drewAgain(page, () => page.locator(ROWS).first().click());
      // The highlight timer repaints once more after the count; let that land too.
      await page.waitForTimeout(400);
      expect(await hidden(page), `hidden nodes after row click ${i + 1}`).toBe(0);
      expect(await edges(page), `edges after row click ${i + 1}`).toBe(reference[i]);
    }
  });

  // Passes without the fix too (a group click does not lose the race on this page); kept as a guard.
  test('a group click with the panel open leaves every node measured too', async ({ page }) => {
    await page.getByRole('button', BUTTON).click();
    // The panel sits over the top left, so take the last group drawn rather than one by name.
    await drewAgain(page, async () => {
      await page.locator('.react-flow__node .cf-group').last().click();
      await page.locator(PICKER).getByRole('button', { name: 'Show all', exact: true }).click();
    });
    await page.waitForTimeout(400);
    expect(await hidden(page)).toBe(0);
    expect(await edges(page)).toBeGreaterThan(0);
  });
});
