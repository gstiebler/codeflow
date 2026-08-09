/**
 * Where each node goes, posed as an ELK problem rather than computed as pixels.
 *
 * Cytoscape reaches ELK through cytoscape-elk and never says what came back. React Flow positions
 * nothing at all, so the layout has to be driven directly - which is the good half of that bargain:
 * the *input* becomes a value, and a value can be checked without a browser. Only running ELK and
 * writing the answer into React Flow's state needs one.
 *
 * No imports beyond types, no DOM, no library: `node --test` reads this file directly.
 */
import type { Id, View } from './types.ts';

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
 * The options the Cytoscape page already uses.
 *
 * As strings, which is not a style choice: cytoscape-elk converts numbers on the way through and
 * the ELK API does not, so a number here is dropped silently - the layout still runs, just without
 * the spacing, which reads as ELK having ignored the request rather than as a type error.
 */
export const ELK_OPTIONS: Record<string, string> = {
  algorithm: 'layered',
  'elk.direction': 'DOWN',
  'elk.layered.spacing.nodeNodeBetweenLayers': '40',
  'elk.spacing.nodeNode': '25',
  // Without this ELK lays each container out independently and the boxes overlap.
  'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
};

/**
 * A leaf's size, guessed from its caption.
 *
 * Cytoscape measures the rendered label and sizes the node to it; ELK has to be told a size before
 * anything exists to measure. Being a few pixels out shows up as looser or tighter spacing and never
 * as a wrong graph, which is why this is a formula and not a render-measure-relayout pass.
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
