import { test } from 'node:test';
import assert from 'node:assert/strict';
import ELK from 'elkjs/lib/elk.bundled.js';
import { loadCorpus } from './corpus.ts';
import { condensed, condensedCaption, condensedOpening, tapCondensed } from '../../../main/resources/viewer/condensed.ts';
import {
  flatEdgeId, flatElkGraph, positions, routes, type ElkNode, type Point, type Positioned,
} from '../../../main/resources/viewer/layout.ts';
import type { CondensedView, Id } from '../../../main/resources/viewer/types.ts';

const elk = new ELK();

/**
 * Does the axis-aligned segment a-b enter the box, more than a pixel in from its border? "Axis-aligned"
 * to a hundredth of a pixel: ELK's coordinates carry floating-point noise in the last digit.
 */
function enters(a: Point, b: Point, box: Positioned): boolean {
  const [left, right, top, bottom] = [box.x + 1, box.x + box.width - 1, box.y + 1, box.y + box.height - 1];
  if (Math.abs(a.y - b.y) < 0.01) return a.y > top && a.y < bottom && Math.max(a.x, b.x) > left && Math.min(a.x, b.x) < right;
  return a.x > left && a.x < right && Math.max(a.y, b.y) > top && Math.min(a.y, b.y) < bottom;
}

const square = (a: Point, b: Point) => Math.abs(a.y - b.y) < 0.01 || Math.abs(a.x - b.x) < 0.01;

/** What is wrong with one laid-out condensed view's routes, or nothing. */
function faults(view: CondensedView, laidOut: ElkNode): string[] {
  const found: string[] = [];
  const at = new Map(positions(laidOut).map((p) => [p.id, p]));
  const read = routes(laidOut);
  for (const edge of view.edges) {
    const id = flatEdgeId(edge);
    const route = read.get(id);
    if (route === undefined) { found.push(`${id} has no route`); continue; }
    // A diagonal is reported once, rather than reasoned about against every box.
    if (route.some((p, i) => i > 0 && !square(route[i - 1], p))) { found.push(`${id} is not at right angles`); continue; }
    for (let i = 1; i < route.length; i++) {
      for (const box of at.values()) {
        if (enters(route[i - 1], route[i], box)) found.push(`${id} runs through ${box.id}`);
      }
    }
  }
  return found;
}

// The condensed view on every fixture, at its opening and after every click of a click-everything
// walk, laid out as the page lays it out - seeded from the drawing before. Every line gets one route,
// at right angles, and no route runs through any node: not another one, and not its own two either.
test('every condensed layout of the corpus routes every line clear of every node', async () => {
  const found: string[] = [];
  let layouts = 0;
  let lines = 0;
  for (const { name, payload } of loadCorpus()) {
    let revealed = condensedOpening(payload);
    let previous: Map<Id, Point> | undefined;
    const draw = async () => {
      const view = condensed(payload, revealed);
      const laidOut = await elk.layout(flatElkGraph(view, condensedCaption, previous)) as ElkNode;
      previous = new Map(positions(laidOut).map((p) => [p.id, { x: p.x, y: p.y }]));
      layouts += 1;
      lines += view.edges.length;
      for (const fault of faults(view, laidOut)) found.push(`${name}: ${fault}`);
    };
    await draw();
    const leaves = payload.nodes.filter((n) => n.type !== 'METHOD').length;
    for (let round = 0; round <= leaves; round += 1) {
      const before = condensed(payload, revealed);
      if (!before.nodes.some((n) => n.type === 'GROUP')) break;
      for (const node of before.nodes) {
        const was = revealed.size;
        revealed = tapCondensed(payload, revealed, { node: node.id });
        if (revealed.size !== was) await draw();
      }
    }
  }
  // The pair: the walk laid out more than the openings, and drew lines.
  assert.ok(layouts > 100, `only ${layouts} layouts`);
  assert.ok(lines > 0);
  assert.deepEqual(found, []);
});
