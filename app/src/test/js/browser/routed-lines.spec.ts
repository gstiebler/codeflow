import { test, expect, type Page } from '@playwright/test';
import { drewAgain, open } from './page.ts';

declare global {
  interface Window { lineFrames?: { moving: boolean; curved: boolean }[] }
}

/** A drawn node: a frame is the outline around some, not one of them. */
const NODE = '.react-flow__node:not(.react-flow__node-condensedFrame)';
const LEAF = '.react-flow__node .cf-node';
const EVENT = 'event entrypoint';

type Box = { x: number; y: number; width: number; height: number };
type Drawn = {
  nodes: { id: string; box: Box }[];
  edges: { id: string; d: string; points: { x: number; y: number }[] }[];
  pane: Box;
};

/**
 * What is on screen, in screen pixels: every node's box, and every edge's drawn path sampled every
 * two pixels along its length - the path as the browser draws it, not the route the page meant.
 */
const drawn = (page: Page): Promise<Drawn> => page.evaluate(() => {
  const box = (r: DOMRect) => ({ x: r.x, y: r.y, width: r.width, height: r.height });
  return {
    // Not a frame: the lines inside it run through its box by design; its nodes are the obstacles.
    nodes: [...document.querySelectorAll<HTMLElement>('.react-flow__node:not(.react-flow__node-condensedFrame)')]
      .map((n) => ({ id: n.dataset.id!, box: box(n.getBoundingClientRect()) })),
    edges: [...document.querySelectorAll('.react-flow__edge')].map((edge) => {
      const path = edge.querySelector<SVGPathElement>('.react-flow__edge-path')!;
      const toScreen = path.getScreenCTM()!;
      const length = path.getTotalLength();
      const points = [];
      for (let s = 0; s < length; s += 2) points.push(path.getPointAtLength(s).matrixTransform(toScreen));
      points.push(path.getPointAtLength(length).matrixTransform(toScreen));
      return {
        id: edge.getAttribute('data-testid')!.replace(/^rf__edge-/, ''),
        d: path.getAttribute('d') ?? '',
        points: points.map((p) => ({ x: p.x, y: p.y })),
      };
    }),
    pane: box(document.querySelector('.react-flow')!.getBoundingClientRect()),
  };
});

const inside = (p: { x: number; y: number }, b: Box, by: number) =>
  p.x > b.x + by && p.x < b.x + b.width - by && p.y > b.y + by && p.y < b.y + b.height - by;

/**
 * Every place a drawn line runs through a node's box. A line may touch its own two nodes where it
 * leaves and arrives - the first and last few pixels - and nowhere else; every other node, never.
 */
function crossings(page: Drawn): string[] {
  const found = new Set<string>();
  for (const edge of page.edges) {
    const [source, target] = edge.id.split('->');
    const [first, last] = [edge.points[0], edge.points[edge.points.length - 1]];
    const near = (p: { x: number; y: number }, q: { x: number; y: number }) => Math.hypot(p.x - q.x, p.y - q.y) <= 3;
    for (const p of edge.points) {
      for (const node of page.nodes) {
        if (!inside(p, node.box, 1)) continue;
        if (node.id === source && near(p, first)) continue;
        if (node.id === target && near(p, last)) continue;
        found.add(`${edge.id} through ${node.id}`);
      }
    }
  }
  return [...found].sort();
}

/** Every line whose first point is not on its source's box or whose last is not on its target's. */
function detached(page: Drawn): string[] {
  const box = new Map(page.nodes.map((n) => [n.id, n.box]));
  const on = (p: { x: number; y: number }, id: string) => inside(p, box.get(id)!, -3);
  return page.edges.filter((edge) => {
    const [source, target] = edge.id.split('->');
    return !on(edge.points[0], source) || !on(edge.points[edge.points.length - 1], target);
  }).map((edge) => edge.id).sort();
}

/** Points of a drawn line outside the pane the page is drawn in. */
const offPane = (page: Drawn) => page.edges.flatMap((edge) =>
  edge.points.filter((p) => !inside(p, page.pane, -1)).map(() => edge.id));

/** A curve command: what React Flow's own bezier draws, and a routed line never does. */
const curved = (d: string) => /C/.test(d);

/**
 * Lines in the condensed view follow the routes ELK computed around the nodes it placed, rather than
 * a curve from one fixed handle to another - which cut through nodes, and looped a line going back
 * up around and off the page. Asked of the drawn paths, on `boundary`'s opening and after a click.
 */
test.describe('boundary, condensed lines', () => {
  test.beforeEach(open('boundary'));

  test('the opening draws every line outside every node box but its own two ends', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(11);
    const on = await drawn(page);
    // The pair: there are lines, each with a length to sample.
    expect(on.edges.length).toBe(26);
    for (const edge of on.edges) expect(edge.points.length, edge.id).toBeGreaterThan(5);
    expect(crossings(on)).toEqual([]);
  });

  test('the opening fits the whole drawing, every line on screen', async ({ page }) => {
    await expect(page.locator(NODE)).toHaveCount(11);
    const on = await drawn(page);
    expect(on.edges.length).toBe(26);
    expect(on.nodes.every((n) => inside({ x: n.box.x + 1, y: n.box.y + 1 }, on.pane, -1))).toBe(true);
    expect(offPane(on)).toEqual([]);
  });

  test('during the move lines follow the gliding nodes, and once it ends they are routed again', async ({ page }) => {
    expect((await drawn(page)).edges.some((e) => curved(e.d))).toBe(false);
    // Every frame from the click until the layout is counted: is the page moving, and is any line a curve.
    await page.evaluate(() => {
      const was = window.drawn ?? 0;
      window.lineFrames = [];
      const tick = () => {
        window.lineFrames!.push({
          moving: document.querySelector('.react-flow.cf-moving') !== null,
          curved: [...document.querySelectorAll('.react-flow__edge-path')].some((p) => /C/.test(p.getAttribute('d') ?? '')),
        });
        if ((window.drawn ?? 0) === was) requestAnimationFrame(tick);
      };
      tick();
    });
    await drewAgain(page, () => page.locator(LEAF).filter({ hasText: EVENT }).click());
    const frames = await page.evaluate(() => window.lineFrames!);
    const moving = frames.filter((f) => f.moving);
    expect(moving.length, 'the page never said it was moving').toBeGreaterThan(2);
    // While the nodes glide, a route laid out for where they will be would not meet them: curves.
    expect(moving.some((f) => f.curved)).toBe(true);

    const on = await drawn(page);
    expect(on.nodes.length).toBeGreaterThan(5);
    expect(on.edges.length).toBeGreaterThan(12);
    expect(on.edges.filter((e) => curved(e.d)).map((e) => e.id)).toEqual([]);
    expect(crossings(on)).toEqual([]);
  });
});

/** The Curved toggle: every line becomes ELK's spline and back, with the same nodes on screen. */
test.describe('boundary, line style', () => {
  test.beforeEach(open('boundary'));

  test('Curved draws every line as a curve, and a second click restores the square lines', async ({ page }) => {
    const button = page.getByRole('button', { name: 'Curved', exact: true });
    await expect(button).toHaveAttribute('aria-pressed', 'false');
    const nodes = await page.locator(NODE).count();
    await drewAgain(page, () => button.click());
    await expect(button).toHaveAttribute('aria-pressed', 'true');
    await expect(page.locator(NODE)).toHaveCount(nodes);
    const on = await drawn(page);
    expect(on.edges.length).toBeGreaterThan(0);
    for (const edge of on.edges) expect(curved(edge.d), edge.id).toBe(true);
    await drewAgain(page, () => button.click());
    await expect(button).toHaveAttribute('aria-pressed', 'false');
    await expect(page.locator(NODE)).toHaveCount(nodes);
    expect((await drawn(page)).edges.some((e) => curved(e.d))).toBe(false);
  });
});
