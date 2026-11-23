/**
 * The condensed view, decided without a browser: the hidden leaves gathered into `program`, with a
 * call-site method box drawn only when needed, opened up where the reader clicks.
 *
 * No imports beyond types, no DOM: `node --test` reads this file directly, and the renderer's bundle
 * imports it. Every function is a pure function of (payload, revealed). `revealed` is the set of leaves taken out of their groups and is the only
 * state; `condensed` derives everything drawn from it, so nothing can go stale between clicks. It holds leaves and,
 * as the one exception, the ids of method boxes: a METHOD id in it means "this box is open" (`groupOf`) and is
 * never drawn or counted as a leaf.
 *
 * A click is first a list - `tapChoices`, what it could show, by section - and the page lets the reader pick from
 * it when it is longer than `PICK_AT`; `tapCondensed` is the whole list at once. `boundaryChoices` is the other
 * list: every boundary call site, to add one box at a time from the toolbar.
 *
 * A hidden leaf is drawn in its call-site box (the METHOD box it sits directly in, `parent`) when the
 * box is an entry point's (no parent) or is open - a member is revealed, or the box's own id is, or (a box that
 * is not a boundary) a hidden member has an edge of any kind but EVENT to a revealed leaf (either direction) -
 * and in `program` otherwise. A boundary box opens only on request: its id or one of its leaves revealed (the
 * Boundaries list, the picker); it is drawn with its own look. Boxes are flat, never inside one another. A
 * leaf with no box is a payload this viewer was not written for, and throws.
 *
 * An EVENT edge is drawn only when it says something the reader can follow (`counts`): its target is revealed,
 * or sits hidden in the event's own box, where the entry's `N more` stands for it. Into `program` or another box
 * it is not drawn - the event starts nearly everything, and thirty lines out of it say nothing.
 *
 * A revealed leaf is drawn inside its method's frame (`CondensedView.frames`): a box with at least one of its
 * own leaves revealed is *open* and gets one, holding those leaves and, while it still has hidden members, its
 * group - the rest node, captioned `N more`. A frame is not a node: no edge ends on it, and it is never inside
 * another frame. A method id revealed alone, with no leaf of the box, draws the plain box and no frame.
 */
import type {
  CondensedEdge, CondensedFrame, CondensedNode, CondensedTarget, CondensedView, EdgeKind, Id, Payload, PayloadEdge, PayloadNode,
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
 * Whether a payload edge is drawn, given what is revealed. Every kind but EVENT always; an EVENT edge only
 * into a revealed leaf or into a leaf hidden in the source's own box - the entry's `N more`. Everything that
 * decides what an edge stands for goes through here: `condensed`, `underlying`, the frontier in `tapChoices`.
 */
function counts(ix: Index, revealed: Set<Id>, edge: PayloadEdge): boolean {
  return edge.kind !== 'EVENT' || revealed.has(edge.target) || ix.box.get(edge.target) === ix.box.get(edge.source);
}

/**
 * Hidden leaf -> where it is drawn: its box when that is an entry box or open, else `program`. Revealed
 * leaves are absent. A box is open when a leaf of it (or its own id) is revealed, or - a box that is not a
 * boundary - a hidden leaf of it has an edge of any kind but EVENT to a revealed leaf, either direction (EVENT
 * edges fan out from the entry event to nearly everything it starts, and would open the whole opening). A
 * boundary box is opened only on request, so a flow into it leaves its leaf in `program`.
 */
export function groupOf(payload: Payload, revealed: Set<Id>): Map<Id, Id> {
  const ix = index(payload);
  const open = new Set<Id>();
  for (const id of revealed) {
    // A leaf opens its box; a method id is a box opened on its own.
    const box = ix.box.get(id) ?? (ix.byId.get(id)?.type === 'METHOD' ? id : undefined);
    if (box !== undefined) open.add(box);
  }
  for (const e of payload.edges) {
    if (e.kind === 'EVENT') continue;
    const [a, b] = [revealed.has(e.source), revealed.has(e.target)];
    const contact = a && !b ? ix.box.get(e.target)! : b && !a ? ix.box.get(e.source)! : undefined;
    if (contact !== undefined && ix.byId.get(contact)?.boundary !== true) open.add(contact);
  }
  // The entry method's box is drawn from the start; the methods it calls are in `program` until the reader opens them.
  const isRoot = (box: PayloadNode) => box.parent === undefined;
  const groups = new Map<Id, Id>();
  for (const leaf of ix.leaves) {
    if (revealed.has(leaf.id)) continue;
    const box = ix.box.get(leaf.id)!;
    groups.set(leaf.id, open.has(box) || isRoot(ix.byId.get(box)!) ? box : PROGRAM);
  }
  return groups;
}

/** Whether a display id is a group, drawn or not: `program` or a method box. */
const isGroup = (ix: Index, id: Id) => id === PROGRAM || ix.members.has(id);

/**
 * What the condensed view opens on, and what Reset returns to: per entry point (a METHOD with no
 * parent) its event and the returns of its own box. Neither the parameters nor what the event starts are
 * shown: the event's one edge, into the entry's `N more`, is where the reader clicks, and its picker lists
 * the parameters first.
 */
export function condensedOpening(payload: Payload): Set<Id> {
  const byId = new Map(payload.nodes.map((n) => [n.id, n]));
  const inRoot = (n: PayloadNode) => n.parent !== undefined && byId.get(n.parent)?.parent === undefined;
  const opening = new Set<Id>();
  for (const n of payload.nodes) {
    if (n.type === 'EVENT' || (n.type === 'RETURN' && inRoot(n))) opening.add(n.id);
  }
  return opening;
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

  // A box is open - framed - when a leaf of its own is revealed, in payload order.
  const frames: CondensedFrame[] = [];
  const framed = new Map<Id, Id>();
  for (const box of payload.nodes) {
    if (box.type !== 'METHOD' || !(ix.members.get(box.id) ?? []).some((m) => revealed.has(m))) continue;
    const id = `frame:${box.id}`;
    framed.set(box.id, id);
    frames.push({
      id, box: box.id, label: ix.label.get(box.id)!,
      ...(box.boundary ? { boundary: true as const } : {}),
      ...(box.assumed ? { assumed: true as const } : {}),
    });
  }

  const nodes: CondensedNode[] = [];
  // `program` first, then the boxes in payload order; a group with no hidden member is not drawn.
  for (const id of [PROGRAM, ...ix.members.keys()]) {
    const count = left.get(id) ?? 0;
    if (count === 0) continue;
    const raw = ix.byId.get(id);
    nodes.push({
      id, label: ix.label.get(id)!, type: 'GROUP', members: count,
      ...(raw?.boundary ? { boundary: true } : {}),
      ...(raw?.assumed ? { assumed: true } : {}),
      ...(framed.has(id) ? { frame: framed.get(id) } : {}),
    });
  }
  for (const leaf of ix.leaves) {
    if (!revealed.has(leaf.id)) continue;
    nodes.push({
      id: leaf.id, label: leaf.label, type: leaf.type, ...(leaf.lead === undefined ? {} : { lead: leaf.lead }),
      frame: framed.get(ix.box.get(leaf.id)!)!,
    });
  }

  const drawn = new Map<string, CondensedEdge>();
  for (const edge of payload.edges) {
    if (!counts(ix, revealed, edge)) continue;
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

  return { nodes, edges: [...drawn.values()], frames };
}

/**
 * What a display node is captioned: a leaf its label, a group its label and how many it stands for -
 * `fetch (42)` - so a click that takes members out visibly changes the group too. A group inside its
 * box's frame is the rest of that box, and the frame already says whose: `39 more`.
 */
export function condensedCaption(node: CondensedNode): string {
  if (node.type !== 'GROUP') return node.label;
  return node.frame === undefined ? `${node.label} (${node.members})` : `${node.members} more`;
}

/** The payload edges a drawn edge stands for, of every kind. */
function underlying(payload: Payload, revealed: Set<Id>, [source, target]: [Id, Id]): PayloadEdge[] {
  const ix = index(payload);
  const shown = shownAs(groupOf(payload, revealed), revealed);
  return payload.edges.filter((e) => counts(ix, revealed, e) && shown(e.source) === source && shown(e.target) === target);
}

/** More than this many choices and a click asks which to show. */
export const PICK_AT = 3;

/** One thing a click could show: a leaf, or a method box to open (then `reveal` is the box's id). */
export type Choice = { id: Id; label: string; section: string; source?: string; reveal: Id[] };

/**
 * What a click could show, by section, only what is not on screen, each id once (the first section
 * to name it wins), in payload order within a section.
 *
 * - a leaf: `comes from` - the hidden sources of the edges into it - and `goes to` - the hidden targets
 *   of the edges out of it;
 * - a method box: `calls` - the boxes it called that are not drawn - then `values` - its frontier (the
 *   hidden members with an edge to something drawn outside the box) or, with none, every hidden member,
 *   so that a click on a group always has something to show;
 * - `program`: `methods` - every box whose hidden members it holds - then `values`, the same;
 * - a drawn edge: `parameters` - the hidden endpoints that are parameters - then `values`, the other hidden
 *   endpoints, of every payload edge it stands for.
 *
 * A member is on a group's frontier only through an edge that is drawn (`counts`).
 */
export function tapChoices(payload: Payload, revealed: Set<Id>, target: CondensedTarget): Choice[] {
  const ix = index(payload);
  const choices: Choice[] = [];
  const listed = new Set<Id>();
  const add = (id: Id, section: string) => {
    if (listed.has(id) || revealed.has(id)) return;
    listed.add(id);
    const { label, source } = ix.byId.get(id)!;
    choices.push({ id, label, section, ...(source === undefined ? {} : { source }), reveal: [id] });
  };

  if ('edge' in target) {
    for (const edge of underlying(payload, revealed, target.edge)) {
      for (const end of [edge.source, edge.target]) add(end, ix.byId.get(end)!.type === 'FUNC_PARAM' ? 'parameters' : 'values');
    }
    // Parameters first, each section in payload order.
    return [...choices.filter((c) => c.section === 'parameters'), ...choices.filter((c) => c.section !== 'parameters')];
  }

  const id = target.node;
  if (!isGroup(ix, id)) {
    const edges = ix.touching.get(id) ?? [];
    for (const edge of edges) if (edge.target === id) add(edge.source, 'comes from');
    for (const edge of edges) if (edge.source === id) add(edge.target, 'goes to');
    return choices;
  }

  const groups = groupOf(payload, revealed);
  const shown = shownAs(groups, revealed);
  const inProgram = (box: PayloadNode) => (ix.members.get(box.id) ?? []).some((m) => groups.get(m) === PROGRAM);
  const boxes = payload.nodes.filter((n) => n.type === 'METHOD' && inProgram(n) && (id === PROGRAM || n.parent === id));
  for (const box of boxes) add(box.id, id === PROGRAM ? 'methods' : 'calls');

  const inside = [...groups].filter(([, g]) => g === id).map(([m]) => m);
  const frontier = inside.filter((m) =>
    (ix.touching.get(m) ?? []).some((e) => counts(ix, revealed, e) && shown(e.source === m ? e.target : e.source) !== id));
  for (const m of frontier.length > 0 ? frontier : inside) add(m, 'values');
  return choices;
}

/**
 * Every boundary call site, for the toolbar's Boundaries list: one choice per METHOD box with
 * `boundary: true`, in payload order, sectioned by the class that declares the method (first
 * appearance). Unlike `tapChoices` it keeps what is already on screen - see `boundaryDrawn`.
 * Revealing a choice's `reveal` (the box id) opens that box, nested or not.
 */
export function boundaryChoices(payload: Payload, _revealed: Set<Id>): Choice[] {
  const rows = payload.nodes.filter((n) => n.type === 'METHOD' && n.boundary === true).map((box) => {
    if (box.method === undefined) throw new Error(`boundary box ${box.id} has no method`);
    const owner = box.method.split('(')[0].split('#')[0];
    return {
      id: box.id, label: box.label, section: owner.slice(owner.lastIndexOf('.') + 1),
      ...(box.source === undefined ? {} : { source: box.source }), reveal: [box.id],
    };
  });
  const sections = [...new Set(rows.map((c) => c.section))];
  return sections.flatMap((section) => rows.filter((c) => c.section === section));
}

/** Whether a boundary choice's box is on screen: a group in `condensed`, or its members all out as leaves. */
export function boundaryDrawn(payload: Payload, revealed: Set<Id>, choice: Choice): boolean {
  const members = index(payload).members.get(choice.id) ?? [];
  const groups = groupOf(payload, revealed);
  return members.some((m) => revealed.has(m) || groups.get(m) === choice.id);
}

/** The call site a choice names as `File.java:N`; null when it names none. */
export function choiceAt(choice: Choice): string | null {
  const at = where(choice.source);
  return at === null ? null : `${at.name}:${at.line}`;
}

/** The source line a choice names, as the payload has its text; null when it has none. */
export function choiceText(payload: Payload, choice: Choice): string | null {
  const at = where(choice.source);
  return at === null ? null : (payload.lines?.[at.file]?.[at.line] ?? null);
}

/**
 * What a click takes out of the groups, when it takes everything it could (the popup's `Show all`):
 * `revealed` plus every choice's `reveal`. Only ever adds: nothing already shown changes.
 */
export function tapCondensed(payload: Payload, revealed: Set<Id>, target: CondensedTarget): Set<Id> {
  const next = new Set(revealed);
  for (const choice of tapChoices(payload, revealed, target)) for (const id of choice.reveal) next.add(id);
  return next;
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
  const box = ix.byId.get(target.node);
  const at = where(box?.called);
  const extra: string[] = [];
  if (box?.assumed) extra.push('assumed: single impl');
  if (box?.summary) {
    if (box.summary.writes.length > 0) extra.push(`writes: ${box.summary.writes.slice(0, 3).join(', ')}`);
    if (box.summary.boundaries.length > 0) extra.push(`calls: ${box.summary.boundaries.slice(0, 3).join(', ')}`);
    if (box.summary.throws.length > 0) extra.push(`throws: ${box.summary.throws.slice(0, 3).join(', ')}`);
  }
  return [`${ix.label.get(target.node)}: ${inside.length} nodes`, ...(at ? [`called at ${at.name}:${at.line}`] : []), ...extra, ...capped(entries)];
}
