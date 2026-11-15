/**
 * One definition of what a colour means, because a graph that changes colour between two renderings
 * is two claims about one program - the failure this repo cares most about.
 *
 * Separate from the renderer that paints with it so that it stays one definition: the page and the
 * Mermaid document are already two renderings of one graph, and MermaidExporter keeps its own copy
 * because it is Kotlin. That duplication is documented in CLAUDE.md; a third copy inside the page
 * would not be.
 */
import type { NodeType, EdgeKind } from './types.ts';

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
  METHOD:       'rgba(240, 240, 240, 0.60)',
};

/** What a node with no colour of its own is filled with. */
export const NODE_DEFAULT = '#ddd';

// The same strokes MermaidExporter uses, so one graph does not change colour between the two
// renderings. FLOW is absent on purpose - it keeps the default grey, and it is nearly every edge.
export const EDGE_COLOURS: Partial<Record<EdgeKind, string>> = {
  TRUE:      '#2e7d32',
  FALSE:     '#c62828',
  CONDITION: '#6a6a6a',
  // The boundary-order token in and out of a boundary call: which ran first, not a value.
  ORDER:     '#7b1fa2',
};

/** The grey every FLOW edge keeps, which is nearly every edge on the page. */
export const EDGE_DEFAULT = '#999';

/**
 * How an edge of [kind] is drawn: its colour, its caption, and whether it is dashed.
 *
 * Here rather than in the renderer so that it is a pure function `node --test` can load - the
 * renderer imports React Flow and elkjs, which it cannot. CONDITION reads `if` rather than
 * `condition`, and is dashed, as the Mermaid document draws it; ORDER is dashed for the reason the
 * Mermaid `-. order .->` is - it is not a value arriving. FLOW is unlabelled: it is nearly every edge.
 */
export function edgeStyle(kind: EdgeKind): { colour: string; label: string | undefined; dashed: boolean } {
  return {
    colour: EDGE_COLOURS[kind] ?? EDGE_DEFAULT,
    label: kind === 'CONDITION' ? 'if' : (kind === 'FLOW' ? undefined : kind.toLowerCase()),
    dashed: kind === 'CONDITION' || kind === 'ORDER',
  };
}
