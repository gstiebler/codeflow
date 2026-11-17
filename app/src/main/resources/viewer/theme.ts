/**
 * One definition of what a colour means, because a graph that changes colour between two renderings
 * is two claims about one program - the failure this repo cares most about.
 *
 * Separate from the renderer that paints with it so that it stays one definition: the page and the
 * Mermaid document are already two renderings of one graph, and MermaidExporter keeps its own copy
 * because it is Kotlin. That duplication is documented in CLAUDE.md; a third copy inside the page
 * would not be.
 */
import type { CondensedEdge, NodeType, EdgeKind, Payload } from './types.ts';

/**
 * Partial, and deliberately: BASE has no colour of its own and takes NODE_DEFAULT, the same as it
 * does in Mermaid, where it has no classDef either.
 */
export const PALETTE: Partial<Record<NodeType, string>> = {
  // The Mermaid classDef colours, as rgba. OBJ_VARIABLE has no classDef today and
  // renders unstyled there; it gets an explicit colour here rather than silently sharing one.
  LITERAL:      'rgba(0, 255, 0, 0.19)',
  VARIABLE:     'rgba(128, 128, 128, 0.19)',
  OBJ_VARIABLE: 'rgba(128, 200, 128, 0.25)',
  BIN_OP:       'rgba(128, 128, 128, 0.50)',
  FUNC_PARAM:   'rgba(128, 128, 255, 0.19)',
  RETURN:       'rgba(255, 128, 128, 0.50)',
  EXTERNAL:     'rgba(255, 165, 0, 0.25)',
  // Where codeflow stopped rather than something the code does - see GraphNode.Unmodelled.
  UNMODELLED:   'rgba(255, 0, 0, 0.19)',
  // The boundary-order token, in the ORDER edges' purple: the chain's boxes and arrows are one thing.
  BOUNDARIES:   'rgba(123, 31, 162, 0.25)',
  // The entry point's event, in the EVENT edges' teal: what set the root going.
  EVENT:        'rgba(0, 137, 123, 0.25)',
  METHOD:       'rgba(240, 240, 240, 0.60)',
};

/** What a node with no colour of its own is filled with. */
export const NODE_DEFAULT = '#ddd';

/** A leaf's outline, 1 px, whatever its fill. */
export const NODE_OUTLINE = '#999';

/** A condensed-view group's fill: white, so its outline is what says whose it is. */
export const GROUP_FILL = '#fff';

/**
 * A condensed-view group's outline: `program` grey, a boundary method's group in the boundaries
 * purple - the same purple as the token and the ORDER edges, since all three are the boundary.
 */
export const GROUP_OUTLINE = '#999';
export const BOUNDARY_OUTLINE = '#7b1fa2';

/**
 * After a click: the ring that fades off what just appeared, in a blue nothing else on the page uses,
 * and the steady outline on the node that was clicked, until the next click.
 */
export const NEW_RING = '#1e88e5';
export const CLICKED_OUTLINE = '#212121';

// The same strokes MermaidExporter uses, so one graph does not change colour between the two
// renderings. FLOW is absent on purpose - it keeps the default grey, and it is nearly every edge.
export const EDGE_COLOURS: Partial<Record<EdgeKind, string>> = {
  TRUE:      '#2e7d32',
  FALSE:     '#c62828',
  CONDITION: '#6a6a6a',
  // The boundary-order token in and out of a boundary call: which ran first, not a value.
  ORDER:     '#7b1fa2',
  // The entry point's event starting what nothing else feeds: when, not a value.
  EVENT:     '#00897b',
};

/** The grey every FLOW edge keeps, which is nearly every edge on the page. */
export const EDGE_DEFAULT = '#999';

/**
 * What an edge caption sits on: the page's own white, so a caption crossing another line reads as
 * text rather than as a knot. The page has one, light, theme; React Flow's default is the same white,
 * written out here so that it stays one definition.
 */
export const LABEL_BG = '#fff';

/**
 * How an edge of [kind] is drawn: its colour, its caption, and whether it is dashed.
 *
 * Here rather than in the renderer so that it is a pure function `node --test` can load - the
 * renderer imports React Flow and elkjs, which it cannot. CONDITION reads `if` rather than
 * `condition`, and is dashed, as the Mermaid document draws it; ORDER and EVENT are dashed for the
 * reason the Mermaid `-. order .->` and `-. event .->` are - they are not a value arriving. FLOW is unlabelled: it is nearly every edge.
 */
export function edgeStyle(kind: EdgeKind): { colour: string; label: string | undefined; dashed: boolean } {
  return {
    colour: EDGE_COLOURS[kind] ?? EDGE_DEFAULT,
    label: kind === 'CONDITION' ? 'if' : (kind === 'FLOW' ? undefined : kind.toLowerCase()),
    dashed: kind === 'CONDITION' || kind === 'ORDER' || kind === 'EVENT',
  };
}

/**
 * How a condensed edge is drawn. It stands for every payload edge between its two display nodes, so
 * it keeps a kind's colour and dash only when that one kind is all it stands for; mixed, it is drawn
 * as FLOW is - grey, solid - since no one kind's look would be true of it. The caption is the count,
 * `×N`, when there are several, and never a kind's word: colour says the kind when there is one, and
 * the hover lists them all.
 */
export function condensedEdgeStyle(edge: CondensedEdge): { colour: string; label: string | undefined; dashed: boolean } {
  const kinds = Object.keys(edge.kinds) as EdgeKind[];
  const { colour, dashed } = edgeStyle(kinds.length === 1 ? kinds[0] : 'FLOW');
  return { colour, label: edge.count > 1 ? `×${edge.count}` : undefined, dashed };
}

/**
 * What each leaf's fill means, beside the fills, so a new colour without a meaning shows in this one
 * file. METHOD is a box, not a leaf, and BASE is never emitted.
 */
export const NODE_MEANINGS: Partial<Record<NodeType, string>> = {
  LITERAL:      'a constant written in the code',
  VARIABLE:     'a variable holding a primitive (number, boolean, ...)',
  OBJ_VARIABLE: 'a variable holding an object',
  BIN_OP:       'an operator: +, ==, &&, ...',
  FUNC_PARAM:   "a method's parameter",
  RETURN:       'what a method returns',
  EXTERNAL:     'from outside the sources, or a boundary call (call x): not read further',
  UNMODELLED:   'a construct codeflow does not model: the graph is incomplete here',
  BOUNDARIES:   'the order token: which boundary call ran before which',
  EVENT:        "the entry point's event: what set it going",
};

/** What each edge kind means. */
export const EDGE_MEANINGS: Record<EdgeKind, string> = {
  FLOW:      'a value flows into',
  TRUE:      'chosen when the condition is true',
  FALSE:     'chosen when the condition is false',
  CONDITION: 'the condition that chose (if)',
  ORDER:     'ran before: the order of boundary calls, not a value',
  SHARED:    "a static one entry point writes and another reads",
  EVENT:     'the event starts this: when, not a value',
};

export type LegendLeaf = { name: string; fill: string; outline: string; meaning: string };
export type LegendLine = { name: string; colour: string; dashed: boolean; meaning: string };

/**
 * The legend's rows, read off the same constants the renderer paints with: leaf fills and line
 * styles only for the types and kinds this payload has, the containers of the current view, and the
 * two marks a click leaves.
 */
export function legend(payload: Payload, mode: 'boxes' | 'condensed'): { leaves: LegendLeaf[]; boxes: LegendLeaf[]; lines: LegendLine[] } {
  const types = new Set(payload.nodes.map((n) => n.type));
  const kinds = new Set(payload.edges.map((e) => e.kind));
  const leaves: LegendLeaf[] = (Object.keys(NODE_MEANINGS) as NodeType[]).filter((t) => types.has(t)).map((t) => ({
    name: t, fill: PALETTE[t] ?? NODE_DEFAULT, outline: NODE_OUTLINE, meaning: NODE_MEANINGS[t]!,
  }));
  const boxes: LegendLeaf[] = [];
  if (mode === 'boxes') {
    boxes.push({ name: 'method', fill: PALETTE.METHOD!, outline: NODE_OUTLINE, meaning: 'a method body, inlined per call; click to open or fold' });
  } else {
    boxes.push(
      { name: 'method', fill: GROUP_FILL, outline: GROUP_OUTLINE, meaning: "a method's calls gathered; click to open" },
      { name: 'boundary', fill: GROUP_FILL, outline: BOUNDARY_OUTLINE, meaning: 'a boundary method: its body is not read' },
    );
  }
  boxes.push(
    { name: 'just appeared', fill: GROUP_FILL, outline: NEW_RING, meaning: 'new since the last click (fades)' },
    { name: 'clicked', fill: GROUP_FILL, outline: CLICKED_OUTLINE, meaning: 'the node last clicked' },
  );
  const lines = (Object.keys(EDGE_MEANINGS) as EdgeKind[]).filter((k) => kinds.has(k)).map((k) => {
    const { colour, dashed } = edgeStyle(k);
    return { name: k, colour, dashed, meaning: EDGE_MEANINGS[k] };
  });
  return { leaves, boxes, lines };
}
