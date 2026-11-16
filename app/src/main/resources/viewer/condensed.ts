/**
 * The condensed view, decided without a browser: no method boxes, the graph gathered into a group
 * per boundary method plus one for everything else, opened up where the reader clicks.
 *
 * Same contract as model.ts: no imports beyond types, no DOM, and every function a pure function of
 * (payload, revealed). `revealed` is the set of leaves taken out of their groups and is the only
 * state; `condensed` derives everything drawn from it, so nothing can go stale between clicks.
 *
 * A leaf's group is computed from the payload, never guessed: it belongs to a boundary method's
 * group iff its nearest enclosing box is a block of that method (`boundary` and `method` on the box,
 * written by JsonExporter). Every block of one method - one per call site, across roots - carries the
 * same `method`, so they are one group. Everything else, a leaf with no box included, is `program`.
 */
import type {
  CondensedEdge, CondensedNode, CondensedTarget, CondensedView, EdgeKind, Id, Payload, PayloadEdge, PayloadNode,
} from './types.ts';

/** The group of every leaf that is not inside a boundary method's block. */
export const PROGRAM = 'program';

/** How many entries a group's or an edge's hover lists before `+N more`. */
export const HOVER_CAP = 10;

type Index = {
  byId: Map<Id, PayloadNode>;
  /** Every leaf, in payload order. */
  leaves: PayloadNode[];
  group: Map<Id, Id>;
  /** Every group, `program` first, then the boundary methods in the order their first block appears. */
  groups: Id[];
  label: Map<Id, string>;
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
  const group = new Map<Id, Id>();
  const groups: Id[] = [PROGRAM];
  const label = new Map<Id, string>([[PROGRAM, PROGRAM]]);
  const members = new Map<Id, Id[]>([[PROGRAM, []]]);

  for (const leaf of leaves) {
    const box = leaf.parent === undefined ? undefined : byId.get(leaf.parent);
    let id = PROGRAM;
    if (box?.boundary) {
      if (box.method === undefined) throw new Error(`boundary box ${box.id} names no method`);
      id = `boundary:${box.method}`;
      if (!members.has(id)) {
        groups.push(id);
        // The box's caption is the method's simple name; the full `method` is in the hover.
        label.set(id, box.label);
        members.set(id, []);
      }
    }
    group.set(leaf.id, id);
    members.get(id)!.push(leaf.id);
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

  const built = { byId, leaves, group, groups, label, members, touching };
  indexes.set(payload, built);
  return built;
}

/** Leaf -> `boundary:<method>` or `program`. Every leaf of the payload has exactly one. */
export function groupOf(payload: Payload): Map<Id, Id> {
  return new Map(index(payload).group);
}

/**
 * What the condensed view opens on, and what Reset returns to: every entry point's event taken out of
 * its group, so the page shows where the program starts beside the boundary groups and `program`.
 */
export function condensedOpening(payload: Payload): Set<Id> {
  return new Set(payload.nodes.filter((n) => n.type === 'EVENT').map((n) => n.id));
}

/** Where a leaf is drawn: on its own once revealed, else as its group. */
function shownAs(ix: Index, revealed: Set<Id>) {
  return (id: Id): Id => {
    if (revealed.has(id)) return id;
    const group = ix.group.get(id);
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
 * pair are one edge per kind with a count, kinds kept apart so the ORDER/TRUE/FALSE colours still
 * mean something.
 */
export function condensed(payload: Payload, revealed: Set<Id>): CondensedView {
  const ix = index(payload);
  const shown = shownAs(ix, revealed);

  const left = new Map<Id, number>();
  for (const leaf of ix.leaves) {
    if (!revealed.has(leaf.id)) left.set(ix.group.get(leaf.id)!, (left.get(ix.group.get(leaf.id)!) ?? 0) + 1);
  }

  const nodes: CondensedNode[] = [];
  for (const id of ix.groups) {
    const count = left.get(id) ?? 0;
    // A group whose last member came out is gone.
    if (count === 0) continue;
    nodes.push({ id, label: ix.label.get(id)!, type: 'GROUP', members: count, ...(id === PROGRAM ? {} : { boundary: true }) });
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
    const key = `${source}\u0000${target}\u0000${edge.kind}`;
    const was = drawn.get(key);
    if (was) was.count += 1;
    else drawn.set(key, { source, target, kind: edge.kind, count: 1 });
  }

  return { nodes, edges: [...drawn.values()] };
}

/**
 * What a display node is captioned: a leaf its label, a group its label and how many it stands for -
 * `program (42)` - so a click that takes members out visibly changes the group too.
 */
export function condensedCaption(node: CondensedNode): string {
  return node.type === 'GROUP' ? `${node.label} (${node.members})` : node.label;
}

/** The payload edges a drawn edge stands for. */
function underlying(payload: Payload, revealed: Set<Id>, [source, target, kind]: [Id, Id, EdgeKind]): PayloadEdge[] {
  const shown = shownAs(index(payload), revealed);
  return payload.edges.filter((e) => e.kind === kind && shown(e.source) === source && shown(e.target) === target);
}

/**
 * What a click takes out of the groups. Only ever adds: nothing already shown changes.
 *
 * - a leaf: its direct neighbours, in and out;
 * - a group: its frontier - members with an edge to something drawn outside the group - or, when it
 *   has none, every member, so that a click on a group always does something;
 * - a drawn edge: the endpoints of every payload edge it stands for.
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
  const members = ix.members.get(id);
  if (members === undefined) {
    for (const edge of ix.touching.get(id) ?? []) {
      next.add(edge.source);
      next.add(edge.target);
    }
    return next;
  }

  const shown = shownAs(ix, revealed);
  const inside = members.filter((m) => !revealed.has(m));
  const frontier = inside.filter((m) =>
    (ix.touching.get(m) ?? []).some((e) => shown(e.source === m ? e.target : e.source) !== id));
  for (const m of frontier.length > 0 ? frontier : inside) next.add(m);
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

const capped = (entries: string[]) => entries.length <= HOVER_CAP
  ? entries
  : [...entries.slice(0, HOVER_CAP), `+${entries.length - HOVER_CAP} more`];

/**
 * The tooltip, as lines.
 *
 * - a leaf: the method it was created in (`Class#method`) and its line with the text;
 * - a group: its member count, then each method its members come from with the range of their
 *   lines, first [HOVER_CAP] then `+N more`;
 * - a drawn edge: where the value arrives - its target's method and line - one entry per distinct
 *   line of the edges it stands for, capped the same way. The target is an approximation, allowed
 *   by the spec: for a phi or a call it is where the value lands, not the expression that moved it.
 */
export function hoverText(payload: Payload, revealed: Set<Id>, target: CondensedTarget): string[] {
  const ix = index(payload);

  if ('edge' in target) {
    const entries = new Set<string>();
    for (const edge of underlying(payload, revealed, target.edge)) {
      const leaf = ix.byId.get(edge.target)!;
      entries.add([methodOf(ix, leaf), lineOf(payload, leaf.source)].filter((s) => s !== null).join('  '));
    }
    return capped([...entries]);
  }

  const members = ix.members.get(target.node);
  if (members === undefined) {
    const leaf = ix.byId.get(target.node);
    if (leaf === undefined) throw new Error(`no node ${target.node} in the payload`);
    return [methodOf(ix, leaf), lineOf(payload, leaf.source)].filter((s) => s !== null);
  }

  const inside = members.filter((m) => !revealed.has(m));
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
  return [`${ix.label.get(target.node)}: ${inside.length} nodes`, ...capped(entries)];
}
