import { test, expect, type Page } from '@playwright/test';
import { drewAgain, frameOf, open, PICKER, tapGroupAll, tapLeafAll } from './page.ts';

const NODE = '.react-flow__node:not(.react-flow__node-condensedFrame)';
const LEAF = '.react-flow__node .cf-node';
const GROUP = '.react-flow__node .cf-group';

/** How many drawn leaves lie wholly inside a frame's rectangle. */
const leavesInside = (page: Page, label: string) => page.evaluate((name) => {
  const frame = [...document.querySelectorAll('.react-flow__node-condensedFrame')]
    .find((f) => (f.textContent ?? '').trim() === name)!.getBoundingClientRect();
  return [...document.querySelectorAll('.react-flow__node .cf-node')].filter((n) => {
    const r = n.getBoundingClientRect();
    return r.left >= frame.left && r.right <= frame.right && r.top >= frame.top && r.bottom <= frame.bottom;
  }).length;
}, label);

/**
 * `boundary` at its opening: the entry method has values out, so it is a frame holding its two leaves
 * (the event and its return) and its rest node, `13 more`; the boundary boxes are not drawn until asked for.
 */
test.describe('frames on boundary', () => {
  test.beforeEach(open('boundary'));

  test('the entry box is a frame, and its two leaves lie inside it', async ({ page }) => {
    await expect(page.locator('.react-flow__node-condensedFrame')).toHaveCount(1);
    await expect(frameOf(page, 'entrypoint')).toHaveCount(1);
    expect(await leavesInside(page, 'entrypoint')).toBe(2);
    await expect(page.locator(LEAF)).toHaveCount(2);
  });

  test('the rest node reads N more, and lies inside the frame too', async ({ page }) => {
    const rest = page.locator('.react-flow__node[data-id="b0"] .cf-group');
    await expect(rest).toHaveText('13 more');
    const [inner, outer] = await Promise.all([rest.boundingBox(), frameOf(page, 'entrypoint').boundingBox()]);
    expect(inner!.x).toBeGreaterThanOrEqual(outer!.x);
    expect(inner!.y + inner!.height).toBeLessThanOrEqual(outer!.y + outer!.height);
  });

  test('clicking the rest node does what clicking the box did: it asks what to show', async ({ page }) => {
    await page.locator('.react-flow__node[data-id="b0"] .cf-group').click();
    await expect(page.locator(PICKER).getByRole('button', { name: 'Show all', exact: true })).toHaveCount(1);
  });

  test('clicking the frame itself is clicking its box', async ({ page }) => {
    // Lines are drawn over the frame, so the click is sent to the frame itself rather than to a point on it.
    await frameOf(page, 'entrypoint').dispatchEvent('click');
    await expect(page.locator(PICKER).getByRole('button', { name: 'Show all', exact: true })).toHaveCount(1);
  });

  test('a closed box is a plain box with its count, and has no frame', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(4);
    await tapGroupAll(page, 'entrypoint');
    await expect(frameOf(page, 'boundaryfunc1')).toHaveCount(0);
    await expect(page.locator(GROUP).filter({ hasText: /^boundaryfunc1 \(5\)$/ })).toHaveCount(1);
    await expect(page.locator(NODE)).toHaveCount(17);
  });

  test('a boundary box that has a value out is a frame with the boundary outline', async ({ page }) => {
    await tapLeafAll(page, 'event entrypoint');
    await expect(frameOf(page, 'boundaryfunc1').locator('.cf-frame')).toHaveCSS('border-top-color', 'rgb(123, 31, 162)');
  });

  test('no node is hidden', async ({ page }) => {
    const hidden = await page.evaluate(() =>
      [...document.querySelectorAll<HTMLElement>('.react-flow__node')].filter((n) => getComputedStyle(n).visibility === 'hidden').length);
    expect(hidden).toBe(0);
  });
});
