/**
 * How one drawing turns into the next, decided without a browser: which nodes stay, arrive and go,
 * where each starts, and where it is at any moment of the move.
 *
 * Renderer state, not model state. condensed.ts decides what is on screen and knows
 * nothing of motion; this compares two drawings by node id - payload ids, a condensed
 * group's being its box's - all stable across clicks - and the renderer tweens.
 *
 * No imports beyond types, no DOM, no library: `node --test` reads this file directly.
 */
import type { Id } from './types.ts';

export type Point = { x: number; y: number };

/** A drawn node's position, relative to its parent as React Flow and ELK both give it. */
export type Placed = Point & { parent?: Id };

/** A drawing: every node on screen, by id. */
export type Drawn = Map<Id, Placed>;

/** How long a move takes, and how long a new node stays ringed. */
export const MOVE_MS = 350;
export const HIGHLIGHT_MS = 1500;

/**
 * Above this many drawn nodes in the next view the move is skipped and the drawing jumps: a frame
 * re-renders every node, and at Fineract's size that is a slideshow rather than a glide.
 */
export const MOTION_CUTOFF = 2000;

export type Diff = { kept: Id[]; added: Id[]; removed: Id[]; start: Map<Id, Point> };

/** Where a node sits on the page: its position plus every enclosing box's. */
export function absolute(drawn: Drawn, id: Id): Point {
  const at = drawn.get(id)!;
  if (at.parent === undefined || !drawn.has(at.parent)) return { x: at.x, y: at.y };
  const base = absolute(drawn, at.parent);
  return { x: base.x + at.x, y: base.y + at.y };
}

/**
 * Two drawings, compared by id. A kept node starts where it was. An added one grows out of the
 * clicked node's old place - on the page, so in a box other than the clicked node's it starts at that
 * same spot, made relative to where its own box starts - or, with nothing clicked or the clicked node
 * not drawn before, at its own new place.
 */
export function diffViews(prev: Drawn, next: Drawn, clicked?: Id): Diff {
  const kept = [...next.keys()].filter((id) => prev.has(id));
  const added = [...next.keys()].filter((id) => !prev.has(id));
  const removed = [...prev.keys()].filter((id) => !next.has(id));
  const origin = clicked !== undefined && prev.has(clicked) ? absolute(prev, clicked) : undefined;

  const start = new Map<Id, Point>();
  // Where a node starts on the page, which an added child needs of its box.
  const startOnPage = (id: Id): Point => {
    const at = start.get(id) ?? place(id);
    const parent = next.get(id)!.parent;
    if (parent === undefined || !next.has(parent)) return at;
    const base = startOnPage(parent);
    return { x: base.x + at.x, y: base.y + at.y };
  };
  const place = (id: Id): Point => {
    if (start.has(id)) return start.get(id)!;
    const was = prev.get(id);
    let at: Point;
    // A kept node that changed box - into or out of the condensed view's focus lane - starts where it
    // was on the page, made relative to its new box; its old relative place means nothing there.
    if (was && was.parent === next.get(id)!.parent) at = { x: was.x, y: was.y };
    else if (was) {
      const page = absolute(prev, id);
      const parent = next.get(id)!.parent;
      const base = parent !== undefined && next.has(parent) ? startOnPage(parent) : { x: 0, y: 0 };
      at = { x: page.x - base.x, y: page.y - base.y };
    }
    else if (origin === undefined) at = { x: next.get(id)!.x, y: next.get(id)!.y };
    else {
      const parent = next.get(id)!.parent;
      const base = parent !== undefined && next.has(parent) ? startOnPage(parent) : { x: 0, y: 0 };
      at = { x: origin.x - base.x, y: origin.y - base.y };
    }
    start.set(id, at);
    return at;
  };
  for (const id of next.keys()) place(id);
  return { kept, added, removed, start };
}

/** Fast at first and settling in: the move reads as arriving rather than as being dragged. */
export const easeOut = (t: number) => 1 - (1 - t) ** 3;

/**
 * Every node of `next` at fraction `t` of the move, already eased: from its start to its new place.
 * Nodes that are going stay put; they are only faded.
 */
export function frame(diff: Diff, next: Drawn, t: number): Drawn {
  const e = easeOut(Math.min(1, Math.max(0, t)));
  const at: Drawn = new Map();
  for (const [id, to] of next) {
    const from = diff.start.get(id)!;
    at.set(id, { x: from.x + (to.x - from.x) * e, y: from.y + (to.y - from.y) * e, parent: to.parent });
  }
  return at;
}
