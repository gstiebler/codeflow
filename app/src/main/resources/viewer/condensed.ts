/**
 * The condensed view, decided without a browser: the hidden leaves gathered into `program`, with a
 * call-site method box drawn only when needed, opened up where the reader clicks.
 *
 * No imports beyond types, no DOM: `node --test` reads this file directly, and the renderer's bundle
 * imports it. Every function is a pure function of (payload, revealed). `revealed` is the set of leaves taken out of their groups and is the only
 * state; `condensed` derives everything drawn from it, so nothing can go stale between clicks.
 *
 * A hidden leaf is drawn in its call-site box (the METHOD box it sits directly in, `parent`) when the
 * box is boundary or open - a member is revealed, or a hidden member has an edge of any kind but
 * EVENT to a revealed leaf - and in `program` otherwise. Boxes are flat, never inside one another. A
 * leaf with no box is a payload this viewer was not written for, and throws.
 */
import type {
  CondensedEdge, CondensedNode, CondensedTarget, CondensedView, EdgeKind, Id, Payload, PayloadEdge, PayloadNode,
} from './types.ts';

/**
 * A leaf's caption cut where the renderer changes weight: the node's `lead` and the rest of its `label`.
 *
 * The lead is the payload's, never a prefix found in the text - a source call named `call x` has
 * none and stays plain. A label that does not start with its lead is a payload this viewer was not
 * written for, and it is drawn whole rather than with the wrong letters in bold.
 */
export function captionParts(node: { label: string; lead?: string }): { lead?: string; rest: string } {
  const { label, lead } = node;
  if (lead === undefined || !label.startsWith(lead)) return { rest: label };
  return { lead, rest: label.slice(lead.length) };
}

/** The group of every hidden leaf no box is drawn for; also its label. */
export const PROGRAM = 'program';

/** How many entries a group's or an edge's hover lists before `+N more`. */
export const HOVER_CAP = 10;

/** The order an edge's hover lists its kinds in: `EdgeKind`'s, the compiler holding it to every kind. */
const KIND_ORDER = Object.keys({
  FLOW: 0, TRUE: 0, FALSE: 0, CONDITION: 0, ORDER: 0, SHARED: 0, EVENT: 0,
} satisfies Record<EdgeKind, 0>) as EdgeKind[];

type Index = {
  byId: Map<Id, PayloadNode>;
  /** Every leaf, in payload order. */
  leaves: PayloadNode[];
  /** Leaf -> the box it sits directly in. */
  box: Map<Id, Id>;
  label: Map<Id, string>;
  /** Box -> its leaves, in payload order. */
  members: Map<Id, Id[]>;
  /** Every edge at a leaf, either direction. */
  touching: Map<Id, PayloadEdge[]>;
};

// Derived once per payload: the payload never changes under a page, and a click is then a walk of
// the edges it touches rather than of the whole graph.
const indexes = new WeakMap<Payload, Index>();

function index(payload: Payload): Index {
  const known = indexes.get(payload);
  if (known) return known;

  const byId = new Map(payload.nodes.map((n) => [n.id, n]));
  const leaves = payload.nodes.filter((n) => n.type !== 'METHOD');
  const box = new Map<Id, Id>();
  const label = new Map<Id, string>([[PROGRAM, PROGRAM]]);
  const members = new Map<Id, Id[]>();

  for (const leaf of leaves) {
    const parent = leaf.parent === undefined ? undefined : byId.get(leaf.parent);
    if (parent?.type !== 'METHOD') throw new Error(`leaf ${leaf.id} has no method box`);
    if (!members.has(parent.id)) {
      // The box's caption is the method's simple name; the full `method` is in the hover.
      label.set(parent.id, parent.label);
      members.set(parent.id, []);
    }
    box.set(leaf.id, parent.id);
    members.get(parent.id)!.push(leaf.id);
  }

  const touching = new Map<Id, PayloadEdge[]>();
  const touch = (id: Id, edge: PayloadEdge) => {
    if (!touching.has(id)) touching.set(id, []);
    touching.get(id)!.push(edge);
  };
  for (const edge of payload.edges) {
    touch(edge.source, edge);
    if (edge.target !== edge.source) touch(edge.target, edge);
  }

  const built = { byId, leaves, box, label, members, touching };
  indexes.set(payload, built);
  return built;
}

/**
 * Hidden leaf -> where it is drawn: its box when that is boundary or open, else `program`. Revealed
 * leaves are absent. A box is open when a leaf of it is revealed, or a hidden leaf of it has an edge
 * of any kind but EVENT to a revealed leaf (EVENT edges fan out from the entry event to nearly
 * everything it starts, and would open the whole opening).
 */
export function groupOf(payload: Payload, revealed: Set<Id>): Map<Id, Id> {
  const ix = index(payload);
  const open = new Set<Id>();
  for (const id of revealed) {
    const box = ix.box.get(id);
    if (box !== undefined) open.add(box);
  }
  for (const e of payload.edges) {
    if (e.kind === 'EVENT') continue;
    const [a, b] = [revealed.has(e.source), revealed.has(e.target)];
    if (a && !b) open.add(ix.box.get(e.target)!);
    else if (b && !a) open.add(ix.box.get(e.source)!);
  }
  const groups = new Map<Id, Id>();
  for (const leaf of ix.leaves) {
    if (revealed.has(leaf.id)) continue;
    const box = ix.box.get(leaf.id)!;
    groups.set(leaf.id, open.has(box) || ix.byId.get(box)!.boundary ? box : PROGRAM);
  }
  return groups;
}

/** Whether a display id is a group, drawn or not: `program` or a method box. */
const isGroup = (ix: Index, id: Id) => id === PROGRAM || ix.members.has(id);

/**
 * What the condensed view opens on, and what Reset returns to: every entry point's event taken out of
 * its group, so the page shows where the program starts.
 */
export function condensedOpening(payload: Payload): Set<Id> {
  return new Set(payload.nodes.filter((n) => n.type === 'EVENT').map((n) => n.id));
}

/** Where a leaf is drawn: on its own once revealed, else as its group. */
function shownAs(groups: Map<Id, Id>, revealed: Set<Id>) {
  return (id: Id): Id => {
    if (revealed.has(id)) return id;
    const group = groups.get(id);
    if (group === undefined) throw new Error(`edge endpoint ${id} is not a leaf of the payload`);
    return group;
  };
}

/**
 * Everything drawn: the groups that still stand for something, the revealed leaves, and the edges
 * between them.
 *
 * Each payload edge `u -> v` is drawn between `shown(u)` and `shown(v)`, and dropped when both land
 * on one display node - an edge inside a group says nothing the group does not. Several onto one
 * ordered pair are one edge, whatever their kinds, with a count per kind: one line per kind made the
 * opening a tangle of captions, and the kinds are one hover away.
 */
export function condensed(payload: Payload, revealed: Set<Id>): CondensedView {
  const ix = index(payload);
  const groups = groupOf(payload, revealed);
  const shown = shownAs(groups, revealed);

  const left = new Map<Id, number>();
  for (const group of groups.values()) left.set(group, (left.get(group) ?? 0) + 1);

  const nodes: CondensedNode[] = [];
  // `program` first, then the boxes in payload order; a group with no hidden member is not drawn.
  for (const id of [PROGRAM, ...ix.members.keys()]) {
    const count = left.get(id) ?? 0;
    if (count === 0) continue;
    nodes.push({ id, label: ix.label.get(id)!, type: 'GROUP', members: count, ...(ix.byId.get(id)?.boundary ? { boundary: true } : {}) });
  }
  for (const leaf of ix.leaves) {
    if (!revealed.has(leaf.id)) continue;
    nodes.push({ id: leaf.id, label: leaf.label, type: leaf.type, ...(leaf.lead === undefined ? {} : { lead: leaf.lead }) });
  }

  const drawn = new Map<string, CondensedEdge>();
  for (const edge of payload.edges) {
    const source = shown(edge.source);
    const target = shown(edge.target);
    if (source === target) continue;
    const key = `${source}\u0000${target}`;
    const was = drawn.get(key);
    if (was) {
      was.count += 1;
      was.kinds[edge.kind] = (was.kinds[edge.kind] ?? 0) + 1;
    } else drawn.set(key, { source, target, kinds: { [edge.kind]: 1 }, count: 1 });
  }

  return { nodes, edges: [...drawn.values()] };
}

/**
 * What a display node is captioned: a leaf its label, a group its label and how many it stands for -
 * `fetch (42)` - so a click that takes members out visibly changes the group too.
 */
export function condensedCaption(node: CondensedNode): string {
  return node.type === 'GROUP' ? `${node.label} (${node.members})` : node.label;
}

/** The payload edges a drawn edge stands for, of every kind. */
function underlying(payload: Payload, revealed: Set<Id>, [source, target]: [Id, Id]): PayloadEdge[] {
  const shown = shownAs(groupOf(payload, revealed), revealed);
  return payload.edges.filter((e) => shown(e.source) === source && shown(e.target) === target);
}

/**
 * What a click takes out of the groups. Only ever adds: nothing already shown changes.
 *
 * - a leaf: its direct neighbours, in and out;
 * - a group: its frontier - members with an edge to something drawn outside the group - or, when it
 *   has none, every member, so that a click on a group always does something;
 * - a drawn edge: the endpoints of every payload edge it stands for, of every kind.
 */
export function tapCondensed(payload: Payload, revealed: Set<Id>, target: CondensedTarget): Set<Id> {
  const ix = index(payload);
  const next = new Set(revealed);

  if ('edge' in target) {
    for (const edge of underlying(payload, revealed, target.edge)) {
      next.add(edge.source);
      next.add(edge.target);
    }
    return next;
  }

  const id = target.node;
  if (!isGroup(ix, id)) {
    for (const edge of ix.touching.get(id) ?? []) {
      next.add(edge.source);
      next.add(edge.target);
    }
    return next;
  }

  const groups = groupOf(payload, revealed);
  const shown = shownAs(groups, revealed);
  const inside = [...groups].filter(([, g]) => g === id).map(([m]) => m);
  const frontier = inside.filter((m) =>
    (ix.touching.get(m) ?? []).some((e) => shown(e.source === m ? e.target : e.source) !== id));
  for (const m of frontier.length > 0 ? frontier : inside) next.add(m);
  return next;
}

/**
 * Where a click's path starts, in payload leaves: walked back from `up`, forward from `down`. A leaf
 * is both; a drawn edge is its payload edges' sources and targets, taken under the `revealed` the
 * click landed on. A group is no focus at all: it stands for values that need not be related, and
 * the path of all of them is most of the graph.
 */
export type Focus = { up: Id[]; down: Id[] };

export function focusOf(payload: Payload, revealed: Set<Id>, target: CondensedTarget): Focus | null {
  if ('edge' in target) {
    const edges = underlying(payload, revealed, target.edge);
    return { up: edges.map((e) => e.source), down: edges.map((e) => e.target) };
  }
  if (isGroup(index(payload), target.node)) return null;
  return { up: [target.node], down: [target.node] };
}

/** What is on a focus's path, as drawn: display nodes, and the drawn edges by source -> targets. */
export type FocusPath = { nodes: Set<Id>; edges: Map<Id, Set<Id>> };

/**
 * Everything that flows into the focus, back to where nothing flows in - the entry point - and
 * everything it flows into, on to the boundaries.
 *
 * Walked over payload edges, not drawn ones: a group is a hub on the page, and walking through it
 * would put everything on the path. A leaf still in its group puts the group on the path. ORDER is
 * not walked - it says which call ran first, and following it pulls in every later call.
 *
 * An edge is on the path when both its ends are upstream or both downstream (then it lies on a walk
 * into or out of the focus), or when it runs from a seed up to a seed down - the clicked edge itself.
 */
export function focusPath(payload: Payload, revealed: Set<Id>, focus: Focus): FocusPath {
  const ix = index(payload);
  const walk = (from: Id[], forward: boolean): Set<Id> => {
    const seen = new Set(from);
    const todo = [...from];
    while (todo.length > 0) {
      const id = todo.pop()!;
      for (const e of ix.touching.get(id) ?? []) {
        if (e.kind === 'ORDER') continue;
        const next = forward ? (e.source === id ? e.target : undefined) : (e.target === id ? e.source : undefined);
        if (next !== undefined && !seen.has(next)) {
          seen.add(next);
          todo.push(next);
        }
      }
    }
    return seen;
  };
  const up = walk(focus.up, false);
  const down = walk(focus.down, true);
  const seedUp = new Set(focus.up);
  const seedDown = new Set(focus.down);

  const shown = shownAs(groupOf(payload, revealed), revealed);
  const nodes = new Set([...up, ...down].map(shown));
  const edges = new Map<Id, Set<Id>>();
  for (const e of payload.edges) {
    if (e.kind === 'ORDER') continue;
    const on = (up.has(e.source) && up.has(e.target)) || (down.has(e.source) && down.has(e.target))
      || (seedUp.has(e.source) && seedDown.has(e.target));
    const [source, target] = [shown(e.source), shown(e.target)];
    if (!on || source === target) continue;
    if (!edges.has(source)) edges.set(source, new Set());
    edges.get(source)!.add(target);
  }
  return { nodes, edges };
}

/** `pkg.Outer.Class#name(int)` -> `Class#name`: what a reader calls the method. */
export function shortMethod(method: string): string {
  const [owner, rest] = method.split('(')[0].split('#');
  return `${owner.slice(owner.lastIndexOf('.') + 1)}#${rest}`;
}

/** A source `path:line:column`, as the file's name and the line. Null when it names no line. */
function where(source: string | undefined): { file: string; name: string; line: string } | null {
  const match = source === undefined ? null : /^(.*):(\d+):(\d+)$/.exec(source);
  if (!match) return null;
  const file = match[1];
  return { file, name: file.slice(Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\')) + 1), line: match[2] };
}

/** `Fee.java:42  total = base + tax;` - the text left off when the payload has none for the line. */
function lineOf(payload: Payload, source: string | undefined): string | null {
  const at = where(source);
  if (!at) return null;
  const text = payload.lines?.[at.file]?.[at.line];
  return text === undefined ? `${at.name}:${at.line}` : `${at.name}:${at.line}  ${text}`;
}

/** The method a leaf was created in: its nearest enclosing box's. */
function methodOf(ix: Index, leaf: PayloadNode): string | null {
  const box = leaf.parent === undefined ? undefined : ix.byId.get(leaf.parent);
  return box?.method === undefined ? null : shortMethod(box.method);
}

/**
 * A leaf's call stack, innermost first, as a Java trace prints it: each frame is a box's method and
 * the line running in it - the leaf's own line in its box, then in each enclosing box the line its
 * child was `called` from. A box with no `called` is a root, or a payload that predates it: the
 * walk ends there.
 */
function stackOf(payload: Payload, ix: Index, leaf: PayloadNode): string[] {
  const frame = (box: PayloadNode | undefined, source: string | undefined) =>
    [box?.method === undefined ? null : shortMethod(box.method), lineOf(payload, source)]
      .filter((s) => s !== null).join('  ');
  let box = leaf.parent === undefined ? undefined : ix.byId.get(leaf.parent);
  const frames = [frame(box, leaf.source)];
  while (box?.called !== undefined && box.parent !== undefined) {
    const caller = ix.byId.get(box.parent);
    frames.push(frame(caller, box.called));
    box = caller;
  }
  return frames;
}

const capped = (entries: string[]) => entries.length <= HOVER_CAP
  ? entries
  : [...entries.slice(0, HOVER_CAP), `+${entries.length - HOVER_CAP} more`];

/**
 * The tooltip, as lines.
 *
 * - a leaf: its call stack, innermost first, one `Class#method  File.java:N  text` per frame - see
 *   [stackOf] - capped like the rest;
 * - a group: its member count, then (a box, not `program`) where its box was called (the box's `called`) when that names a line, then
 *   each method its members come from with the range of their lines, first [HOVER_CAP] then `+N more`;
 * - a drawn edge: the kinds it stands for with their counts (`flow ×3, order ×2`, in KIND_ORDER),
 *   since the line itself names none; then where the value arrives - its target's method and line -
 *   one entry per distinct line of the edges it stands for, capped the same way. The target is an
 *   approximation, allowed by the spec: for a phi or a call it is where the value lands, not the
 *   expression that moved it.
 */
export function hoverText(payload: Payload, revealed: Set<Id>, target: CondensedTarget): string[] {
  const ix = index(payload);

  if ('edge' in target) {
    const entries = new Set<string>();
    const kinds = new Map<EdgeKind, number>();
    for (const edge of underlying(payload, revealed, target.edge)) {
      kinds.set(edge.kind, (kinds.get(edge.kind) ?? 0) + 1);
      const leaf = ix.byId.get(edge.target)!;
      entries.add([methodOf(ix, leaf), lineOf(payload, leaf.source)].filter((s) => s !== null).join('  '));
    }
    const counts = KIND_ORDER.filter((k) => kinds.has(k)).map((k) => `${k.toLowerCase()} ×${kinds.get(k)}`);
    return [counts.join(', '), ...capped([...entries])];
  }

  if (!isGroup(ix, target.node)) {
    const leaf = ix.byId.get(target.node);
    if (leaf === undefined) throw new Error(`no node ${target.node} in the payload`);
    return capped(stackOf(payload, ix, leaf));
  }

  const inside = [...groupOf(payload, revealed)].filter(([, g]) => g === target.node).map(([m]) => m);
  // Per method and file, in the order first met: the lowest and highest line its members name.
  const ranges = new Map<string, { head: string; name: string; low: number; high: number }>();
  for (const id of inside) {
    const leaf = ix.byId.get(id)!;
    const head = methodOf(ix, leaf) ?? '(no method)';
    const at = where(leaf.source);
    const key = `${head}\u0000${at?.file ?? ''}`;
    const line = at === null ? NaN : Number(at.line);
    const range = ranges.get(key);
    if (!range) ranges.set(key, { head, name: at?.name ?? '', low: line, high: line });
    else if (!Number.isNaN(line)) {
      range.low = Number.isNaN(range.low) ? line : Math.min(range.low, line);
      range.high = Number.isNaN(range.high) ? line : Math.max(range.high, line);
    }
  }
  const entries = [...ranges.values()].map(({ head, name, low, high }) => {
    if (Number.isNaN(low)) return head;
    return `${head}  ${name}:${low === high ? low : `${low}-${high}`}`;
  });
  const at = where(ix.byId.get(target.node)?.called);
  return [`${ix.label.get(target.node)}: ${inside.length} nodes`, ...(at ? [`called at ${at.name}:${at.line}`] : []), ...capped(entries)];
}
