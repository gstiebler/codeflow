/**
 * Where each node goes, posed as an ELK problem rather than computed as pixels.
 *
 * React Flow positions nothing on its own, so ELK is driven directly rather than through an adapter
 * that hides both ends of it. That is the good half of the bargain: the layout's *input* becomes a
 * value, and a value can be checked without a browser. Only running ELK and writing the answer into
 * React Flow's state needs one.
 *
 * No imports beyond types, no DOM, no library: `node --test` reads this file directly.
 */
import type { CondensedNode, CondensedView, Id, View } from './types.ts';

/** How much of a box's top belongs to its caption rather than to what is inside it. */
export const TITLE_HEIGHT = 22;

export type ElkEdge = { id: string; sources: string[]; targets: string[] };

export type ElkNode = {
  id: string;
  width?: number;
  height?: number;
  x?: number;
  y?: number;
  layoutOptions?: Record<string, string>;
  children?: ElkNode[];
  edges?: ElkEdge[];
};

/**
 * How the graph is laid out. One copy, applied to the root and to every box, since a box laid out
 * by different rules than its parent is a diagram that changes direction halfway down.
 *
 * As strings, which is not a style choice: the ELK API takes strings and drops a number silently -
 * the layout still runs, just without the spacing, which reads as ELK having ignored the request
 * rather than as a type error. An adapter that coerces numbers on the way through is what hid this
 * for as long as there was one.
 */
const ELK_OPTIONS: Record<string, string> = {
  algorithm: 'layered',
  'elk.direction': 'DOWN',
  'elk.layered.spacing.nodeNodeBetweenLayers': '40',
  'elk.spacing.nodeNode': '25',
  // Without this ELK lays each container out independently and the boxes overlap.
  'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
  // Room at the top for the box's caption. A box means a method, so its name is the one label on the
  // page that says which method a value lives in - and ELK's default padding is even on all four
  // sides, which puts the first row of nodes straight over it.
  'elk.padding': `[top=${TITLE_HEIGHT},left=12,bottom=12,right=12]`,
};

/**
 * A leaf's size, guessed from its caption.
 *
 * ELK has to be told a size before anything exists to measure, and the page draws nothing until ELK
 * has answered - so there is no rendered label to measure at the point the number is needed. Being a
 * few pixels out shows up as looser or tighter spacing and never as a wrong graph, which is why this
 * is a formula and not a render-measure-relayout pass.
 */
const CHAR_WIDTH = 6.5;
const PADDING = 16;
export const NODE_HEIGHT = 26;

export const nodeWidth = (badge: string) => Math.round(badge.length * CHAR_WIDTH) + PADDING;

/**
 * The visible subgraph, nested, ready for ELK.
 *
 * Hidden nodes are left out rather than sized zero: ELK reserves space for anything it is given, so
 * a hidden node laid out is a hole in the diagram with nothing on the page to explain it.
 *
 * A box gets no width or height. ELK sizes a parent to fit its children, and a size of ours is
 * honoured instead - which is how a box ends up smaller than what it contains.
 *
 * Every edge is declared at the root, whatever it connects: ELK requires an edge to sit in a graph
 * enclosing both endpoints, and only the root always qualifies. GraphmlExporter has the same rule
 * for the same reason.
 */
export function elkGraph(view: View, previous?: Map<Id, Point>): ElkNode {
  const byParent = new Map<Id | undefined, ElkNode[]>();
  const boxes: ElkNode[] = [];

  for (const node of view.nodes) {
    if (!node.visible) continue;
    const elk: ElkNode = node.type === 'METHOD'
      ? { id: node.id, layoutOptions: ELK_OPTIONS, children: [] }
      : { id: node.id, width: nodeWidth(node.badge), height: NODE_HEIGHT };
    if (node.type === 'METHOD') boxes.push(elk);
    if (!byParent.has(node.parent)) byParent.set(node.parent, []);
    byParent.get(node.parent)!.push(elk);
  }

  // One pass over the boxes rather than a recursion: every box already exists, and a box's children
  // are whatever named it, so there is nothing to walk down to.
  for (const box of boxes) box.children = byParent.get(box.id) ?? [];

  const root: ElkNode = {
    id: 'root',
    layoutOptions: ELK_OPTIONS,
    children: byParent.get(undefined) ?? [],
    edges: view.edges
      .filter((edge) => edge.visible)
      .map((edge) => ({ id: `${edge.source}->${edge.target}`, sources: [edge.source], targets: [edge.target] })),
  };
  return previous ? stabilise(root, previous) : root;
}

/**
 * The condensed view, for ELK: one level, no boxes. Group and leaf alike are sized from the caption
 * they are drawn with, which the caller passes so that the two cannot drift - and so that this file
 * keeps importing nothing but types.
 *
 * The hierarchy options are left out because there is no hierarchy; the spacing is the boxed view's,
 * so switching views does not change how dense the page reads.
 */
const FLAT_OPTIONS: Record<string, string> = {
  algorithm: 'layered',
  'elk.direction': 'DOWN',
  'elk.layered.spacing.nodeNodeBetweenLayers': ELK_OPTIONS['elk.layered.spacing.nodeNodeBetweenLayers'],
  'elk.spacing.nodeNode': ELK_OPTIONS['elk.spacing.nodeNode'],
};

/** With the kind: the condensed view draws one edge per (source, target, kind), so the pair is not unique. */
export const flatEdgeId = (edge: { source: Id; target: Id; kind: string }) =>
  `${edge.source}->${edge.target}:${edge.kind}`;

export function flatElkGraph(
  view: CondensedView, caption: (node: CondensedNode) => string, previous?: Map<Id, Point>,
): ElkNode {
  const root: ElkNode = {
    id: 'root',
    layoutOptions: FLAT_OPTIONS,
    children: view.nodes.map((node) => ({ id: node.id, width: nodeWidth(caption(node)), height: NODE_HEIGHT })),
    edges: view.edges.map((edge) => ({ id: flatEdgeId(edge), sources: [edge.source], targets: [edge.target] })),
  };
  return previous ? stabilise(root, previous) : root;
}

export type Point = { x: number; y: number };

/**
 * What tells ELK to start from the drawing before: layer by the given positions, and keep the given
 * order within a layer while still minimising crossings around it. Model order was the other
 * candidate, and it crashes ELK 0.9.3 inside a hierarchical layout - docs/decisions/viewer.md has
 * that, and the measurements that chose these.
 */
export const INTERACTIVE_OPTIONS: Record<string, string> = {
  'elk.layered.layering.strategy': 'INTERACTIVE',
  'elk.layered.crossingMinimization.semiInteractive': 'true',
};

/**
 * The same ELK problem, posed so that the answer stays close to `previous` - the positions the page
 * drew last, each relative to its parent, as ELK and React Flow both give them.
 *
 * A node that was drawn starts where it was. One that was not starts half a pixel past its first
 * neighbour among its siblings that was - inside that neighbour's layer, just after it - or, with
 * none, wherever ELK puts a node it was given no position for. Siblings only: a position means
 * something only next to others in the same box.
 *
 * A new tree: the input, and the option objects it shares with every other box, are left alone.
 */
export function stabilise(root: ElkNode, previous: Map<Id, Point>): ElkNode {
  const neighbours = new Map<Id, Id[]>();
  const link = (a: Id, b: Id) => {
    if (!neighbours.has(a)) neighbours.set(a, []);
    neighbours.get(a)!.push(b);
  };
  for (const edge of root.edges ?? []) {
    for (const s of edge.sources) for (const t of edge.targets) { link(s, t); link(t, s); }
  }

  const rebuild = (graph: ElkNode): ElkNode => {
    const siblings = new Set((graph.children ?? []).map((c) => c.id));
    const seat = (id: Id): Point | undefined => {
      const at = previous.get(id);
      if (at) return at;
      const old = (neighbours.get(id) ?? []).find((n) => siblings.has(n) && previous.has(n));
      return old === undefined ? undefined : { x: previous.get(old)!.x + 0.5, y: previous.get(old)!.y + 0.5 };
    };
    const children = (graph.children ?? []).map((child) => {
      const at = seat(child.id);
      const placed: ElkNode = at
        ? { ...child, x: at.x, y: at.y, layoutOptions: { ...child.layoutOptions, 'elk.position': `(${at.x},${at.y})` } }
        : child;
      return child.children ? rebuild(placed) : placed;
    });
    return {
      ...graph,
      ...(graph.layoutOptions ? { layoutOptions: { ...graph.layoutOptions, ...INTERACTIVE_OPTIONS } } : {}),
      children,
    };
  };
  return rebuild(root);
}

export type Positioned ={ id: Id; x: number; y: number; width: number; height: number; parent?: Id };

/**
 * The laid-out tree, flattened, each node remembering which box it came out of.
 *
 * ELK returns a child's coordinates relative to its parent, which is already React Flow's `parentId`
 * convention - so nothing is re-based here, and re-basing it would be the bug.
 */
export function positions(laidOut: ElkNode, parent?: Id, into: Positioned[] = []): Positioned[] {
  for (const child of laidOut.children ?? []) {
    into.push({
      id: child.id,
      x: child.x ?? 0,
      y: child.y ?? 0,
      width: child.width ?? 0,
      height: child.height ?? 0,
      parent,
    });
    positions(child, child.id, into);
  }
  return into;
}
