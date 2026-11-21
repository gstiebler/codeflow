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
import type { CondensedNode, CondensedView, Id } from './types.ts';

/** One stretch of a routed edge, as ELK returns it: in the frame of the graph the edge is declared in. */
export type ElkSection = { id: string; startPoint: Point; endPoint: Point; bendPoints?: Point[] };

export type ElkEdge = { id: string; sources: string[]; targets: string[]; sections?: ElkSection[]; container?: string };

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

export const nodeWidth = (caption: string) => Math.round(caption.length * CHAR_WIDTH) + PADDING;

/**
 * The view, for ELK: one level, no method boxes. Group and leaf alike are sized from the caption
 * they are drawn with, which the caller passes so that the two cannot drift - and so that this file
 * keeps importing nothing but types.
 *
 * The hierarchy options are left out because there is no hierarchy. The spacing
 * is wide: a few nodes with long edges between them, each perhaps captioned `×N`, read as a tangle
 * when packed.
 *
 * Orthogonal routes, which the page draws instead of React Flow's handle-to-handle curves: ELK
 * routes around the nodes it placed, so a line never crosses a node, and a line going back up runs
 * down the side rather than looping off the page. Orthogonal over splines because right angles are
 * the easiest to follow by eye from one end to the other.
 */
const FLAT_OPTIONS: Record<string, string> = {
  algorithm: 'layered',
  'elk.direction': 'DOWN',
  'elk.layered.spacing.nodeNodeBetweenLayers': '70',
  'elk.spacing.nodeNode': '40',
  'elk.edgeRouting': 'ORTHOGONAL',
};

/** How the page draws its lines: right-angled routes with rounded corners, or ELK's smooth splines. */
export type LineStyle = 'square' | 'curved';

/** The condensed view draws one edge per ordered pair of display nodes, so the pair is its id. */
export const flatEdgeId = (edge: { source: Id; target: Id }) => `${edge.source}->${edge.target}`;

/**
 * The view, for ELK, flat: every node a child of the root.
 */
export function flatElkGraph(
  view: CondensedView, caption: (node: CondensedNode) => string, previous?: Map<Id, Point>,
  style: LineStyle = 'square',
): ElkNode {
  const options = { ...FLAT_OPTIONS, 'elk.edgeRouting': style === 'curved' ? 'SPLINES' : 'ORTHOGONAL' };
  const root: ElkNode = {
    id: 'root',
    layoutOptions: options,
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
 * something only next to others in the same parent.
 *
 * A new tree: the input, and the option objects it shares with every other parent, are left alone.
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
 * The laid-out tree, flattened, each node remembering which parent it came out of.
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

/**
 * Every root edge's route, by edge id: start, each bend in order, end - in the frame of the root,
 * which is the frame React Flow draws lines in.
 *
 * An edge with no section, or with more than one, is an error rather than a straight line. A simple
 * edge in a flat layered graph always comes back as exactly one section; anything else means the
 * request or ELK changed, and a line drawn from a guess is the line through a node this is here to
 * prevent - drawn with nothing on the page to say it was guessed.
 */
export function routes(laidOut: ElkNode): Map<string, Point[]> {
  // ELK gives an edge it placed inside a compound node in that node's frame, and says which in
  // `container`: added back here, or the line is drawn off by the node's offset.
  const frame = new Map<Id, Point>([[laidOut.id, { x: 0, y: 0 }]]);
  for (const child of laidOut.children ?? []) frame.set(child.id, { x: child.x ?? 0, y: child.y ?? 0 });
  const into = new Map<string, Point[]>();
  for (const edge of laidOut.edges ?? []) {
    const sections = edge.sections ?? [];
    if (sections.length !== 1) {
      throw new Error(`ELK returned ${sections.length} sections for edge ${edge.id}, expected exactly one`);
    }
    const base = frame.get(edge.container ?? laidOut.id);
    if (base === undefined) throw new Error(`ELK put edge ${edge.id} in ${edge.container}, which is not a top-level node`);
    const [only] = sections;
    into.set(edge.id, [only.startPoint, ...(only.bendPoints ?? []), only.endPoint].map(({ x, y }) => ({ x: x + base.x, y: y + base.y })));
  }
  return into;
}

/**
 * Where a route's caption goes: the middle of its longest segment, which is the stretch with the most
 * room beside it and the one least likely to sit on a corner or against a node.
 */
export function captionAt(route: Point[]): Point {
  let best = 0;
  let at: Point = route[0];
  for (let i = 1; i < route.length; i++) {
    const [a, b] = [route[i - 1], route[i]];
    const length = Math.hypot(b.x - a.x, b.y - a.y);
    if (length > best) {
      best = length;
      at = { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 };
    }
  }
  return at;
}

/**
 * An SVG path through every point of a route, each bend rounded off with a quadratic curve of at most
 * `radius`. A corner never takes more than half of either segment beside it, so two corners on one
 * short segment meet rather than overlap.
 */
export function roundedPath(route: Point[], radius: number): string {
  const parts = [`M ${route[0].x} ${route[0].y}`];
  for (let i = 1; i < route.length - 1; i++) {
    const [a, b, c] = [route[i - 1], route[i], route[i + 1]];
    const into = Math.hypot(b.x - a.x, b.y - a.y);
    const out = Math.hypot(c.x - b.x, c.y - b.y);
    const r = Math.min(radius, into / 2, out / 2);
    // A repeated point has no direction to round off along.
    if (r === 0) {
      parts.push(`L ${b.x} ${b.y}`);
      continue;
    }
    const before = { x: b.x - ((b.x - a.x) / into) * r, y: b.y - ((b.y - a.y) / into) * r };
    const after = { x: b.x + ((c.x - b.x) / out) * r, y: b.y + ((c.y - b.y) / out) * r };
    parts.push(`L ${before.x} ${before.y}`, `Q ${b.x} ${b.y} ${after.x} ${after.y}`);
  }
  const last = route[route.length - 1];
  parts.push(`L ${last.x} ${last.y}`);
  return parts.join(' ');
}

/**
 * An SVG path for a route ELK computed with `elk.edgeRouting: SPLINES`. Found by running elkjs 0.9.3:
 * a spline edge still comes back as exactly one section, and its points - startPoint, bendPoints in
 * order, endPoint - are cubic Bezier control points: the start, then one `C` per three points
 * (1 + 3k in all; even a straight edge with no obstacle gets one cubic, e.g. start, start, a point
 * on the line, end; a long straight stretch can have several). A route that is not of that shape,
 * which is the two-point route of an edge with no bends, is drawn as the straight line it is.
 */
export function curvedPath(route: Point[]): string {
  const parts = [`M ${route[0].x} ${route[0].y}`];
  if (route.length < 4 || (route.length - 1) % 3 !== 0) {
    for (const p of route.slice(1)) parts.push(`L ${p.x} ${p.y}`);
  } else {
    for (let i = 1; i < route.length; i += 3) {
      parts.push(`C ${route[i].x} ${route[i].y} ${route[i + 1].x} ${route[i + 1].y} ${route[i + 2].x} ${route[i + 2].y}`);
    }
  }
  return parts.join(' ');
}
