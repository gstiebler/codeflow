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
import type { Id, View } from './types.ts';

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
export function elkGraph(view: View): ElkNode {
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

  return {
    id: 'root',
    layoutOptions: ELK_OPTIONS,
    children: byParent.get(undefined) ?? [],
    edges: view.edges
      .filter((edge) => edge.visible)
      .map((edge) => ({ id: `${edge.source}->${edge.target}`, sources: [edge.source], targets: [edge.target] })),
  };
}

export type Positioned = { id: Id; x: number; y: number; width: number; height: number; parent?: Id };

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
