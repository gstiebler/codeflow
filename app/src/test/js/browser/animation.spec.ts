import { test, expect, type Page } from '@playwright/test';
import { drewAgain, open, tapGroupAll, tapLeaf, tapLeafAll } from './page.ts';

const leaf = (page: Page, label: string) =>
  page.locator('.react-flow__node').filter({ has: page.locator('.cf-node', { hasText: new RegExp(`^${label}( [↑↓].*)*$`) }) }).first();

const idOf = (page: Page, label: string) => leaf(page, label).getAttribute('data-id');

/** One animation frame, as the page drew it: is the drawing moving, and where is the watched node. */
type Seen = { moving: boolean; drawn: number; x: number; y: number; transform: string; viewport: string };

declare global {
  interface Window { seen?: Seen[]; cfAnimationMs?: number }
}

/**
 * Record every frame from now until the next layout is counted: whether the page says it is moving,
 * and where node `id` is - on screen, and in the drawing (its own transform, which the viewport does
 * not touch). The recorder is the page's own requestAnimationFrame, so it sees each frame the move
 * paints and the frame on which `drawn` goes up.
 */
const watch = (page: Page, id: string) => page.evaluate((id) => {
  const was = window.drawn ?? 0;
  const seen: Seen[] = [];
  window.seen = seen;
  const tick = () => {
    const node = [...document.querySelectorAll<HTMLElement>('.react-flow__node')].find((n) => n.dataset.id === id)!;
    const r = node.getBoundingClientRect();
    seen.push({
      moving: document.querySelector('.react-flow.cf-moving') !== null,
      drawn: window.drawn ?? 0, x: r.x, y: r.y, transform: node.style.transform,
      viewport: document.querySelector<HTMLElement>('.react-flow__viewport')!.style.transform,
    });
    if ((window.drawn ?? 0) === was) requestAnimationFrame(tick);
  };
  tick();
}, id);

const seen = (page: Page) => page.evaluate(() => window.seen!);

const ringed = (page: Page, selector: string) => page.evaluate((selector) =>
  [...document.querySelectorAll(`${selector}.cf-new .cf-node`)].map((n) => (n.textContent ?? '').split(' ')[0]).sort(),
  selector);

/**
 * A click moves the drawing rather than replacing it: what the reader was looking at stays put on
 * screen, what is new is marked, and the tests' layout counter waits for all of it. Asked of the DOM,
 * frame by frame, from funcCall's `event main`, whose click takes the entry method's nodes out of
 * its box.
 */
test.describe('funcCall, a click animated', () => {
  test.beforeEach(open('funcCall'));

  test('the clicked node stays within 40 px of where it was on screen while the drawing moves', async ({ page }) => {
    const x = (await idOf(page, 'event main'))!;
    await watch(page, x);
    await tapLeafAll(page, 'event main');
    const frames = await seen(page);
    const moving = frames.filter((f) => f.moving);
    expect(moving.length, 'the page never said it was moving').toBeGreaterThan(2);
    // Moved in the drawing, and the viewport moved against it - so holding it still was work, not luck ...
    expect(new Set(moving.map((f) => f.transform)).size).toBeGreaterThan(2);
    expect(new Set(moving.map((f) => f.viewport)).size).toBeGreaterThan(2);
    // ... and held still on screen.
    const [start] = frames;
    for (const f of moving) expect(Math.hypot(f.x - start.x, f.y - start.y)).toBeLessThan(40);
  });

  test('the layout is counted once the move has ended, not while it runs', async ({ page }) => {
    await watch(page, (await idOf(page, 'event main'))!);
    await tapLeafAll(page, 'event main');
    const frames = await seen(page);
    expect(frames.some((f) => f.moving)).toBe(true);
    const counted = frames.find((f) => f.drawn > frames[0].drawn)!;
    expect(counted.moving).toBe(false);
  });

  test('what a click brought in is ringed, then the ring comes off; what was there is not ringed', async ({ page }) => {
    await tapLeafAll(page, 'event main');
    const fresh = await ringed(page, '.react-flow__node');
    expect(fresh).toContain('11');
    expect(fresh).not.toContain('args');
    expect(fresh).not.toContain('main');
    expect(fresh).not.toContain('event main');
    expect(await page.locator('.react-flow__edge.cf-new').count()).toBeGreaterThan(0);
    await expect(page.locator('.react-flow__node.cf-new')).toHaveCount(0, { timeout: 5000 });
    await expect(page.locator('.react-flow__edge.cf-new')).toHaveCount(0);
    expect(await page.locator('.react-flow__node').count()).toBeGreaterThan(fresh.length);
  });

  test('the last node clicked is outlined until the next click', async ({ page }) => {
    await tapLeafAll(page, 'event main');
    await expect(page.locator('.react-flow__node.cf-clicked')).toHaveCount(1);
    await expect(leaf(page, 'event main')).toHaveClass(/cf-clicked/);
    await tapLeaf(page, 'main');
    await expect(page.locator('.react-flow__node.cf-clicked')).toHaveCount(1);
    await expect(leaf(page, 'main')).toHaveClass(/cf-clicked/);
    await expect(leaf(page, 'event main')).not.toHaveClass(/cf-clicked/);
  });
});

test.describe('funcCall, a click not animated', () => {
  for (const [why, setUp] of [
    ['reduced motion', (page: Page) => page.emulateMedia({ reducedMotion: 'reduce' })],
    ['a zero duration', (page: Page) => page.evaluate(() => { window.cfAnimationMs = 0; })],
  ] as const) {
    test(`with ${why} the drawing jumps, and what is new is still ringed`, async ({ page }) => {
      await open('funcCall')({ page });
      await setUp(page);
      const x = (await idOf(page, 'event main'))!;
      await watch(page, x);
      await tapLeafAll(page, 'event main');
      const frames = await seen(page);
      expect(frames.filter((f) => f.moving)).toEqual([]);
      // Two places at most - before and after - for the node in the drawing: no frame in between.
      expect(new Set(frames.map((f) => f.transform)).size).toBeLessThanOrEqual(2);
      expect(await ringed(page, '.react-flow__node')).toContain('11');
    });
  }

  test('with reduced motion the ring holds still rather than fading', async ({ page }) => {
    await open('funcCall')({ page });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await tapLeafAll(page, 'event main');
    const ring = page.locator('.react-flow__node.cf-new > *').first();
    await expect(ring).toHaveCSS('animation-name', 'none');
    await expect(ring).not.toHaveCSS('box-shadow', 'none');
  });
});

/** Drawn nodes whose box is not wholly inside the page's graph area. */
const outside = (page: Page) => page.evaluate(() => {
  const box = document.querySelector('.react-flow')!.getBoundingClientRect();
  return [...document.querySelectorAll('.react-flow__node')].filter((n) => {
    const r = n.getBoundingClientRect();
    return r.left < box.left || r.top < box.top || r.right > box.right || r.bottom > box.bottom;
  }).length;
});

// Reset answers no click, so nothing anchors the reader and nothing is necessarily new: without its
// own refit, a reader who had panned away comes back to an empty screen.
test.describe('boundary, reset', () => {
  test.beforeEach(open('boundary'));

  test('Reset fits what is left on screen, wherever the reader had panned to', async ({ page }) => {
    await tapGroupAll(page, 'entrypoint');
    const grown = await page.locator('.react-flow__node').count();
    expect(grown).toBeGreaterThan(11);

    // Drag the pane from its bottom-right corner far up and left: the drawing goes with it.
    const box = (await page.locator('.react-flow').boundingBox())!;
    await page.mouse.move(box.x + box.width - 10, box.y + box.height - 10);
    await page.mouse.down();
    await page.mouse.move(box.x + 10, box.y + 10, { steps: 10 });
    await page.mouse.up();
    expect(await outside(page)).toBeGreaterThan(0);

    await drewAgain(page, () => page.getByRole('button', { name: 'Reset', exact: true }).click());
    // The entry method's box and the five boundary boxes the parameters flow into, and five leaves on their own.
    await expect(page.locator('.react-flow__node')).toHaveCount(11);
    expect(await outside(page)).toBe(0);
  });
});
