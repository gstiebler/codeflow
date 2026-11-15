/**
 * The payload's shape and the view's, in one place.
 *
 * JsonExporter writes the payload and is the authority on it; nothing here may add a field. The
 * view is what the renderer is handed, and every field on it is a decision model.ts has already
 * made - so a renderer reaching past this type for something it could work out itself is the
 * boundary going soft.
 */

export type Id = string;

/**
 * Every type JsonExporter emits: `codeflow.graph.NodeType`, plus METHOD for a block.
 *
 * METHOD is a box and everything else is a leaf. That distinction is the one the whole viewer turns
 * on, so it is spelled out here rather than left to a string comparison in each renderer.
 */
export type NodeType =
  | 'METHOD'
  | 'BASE' | 'LITERAL' | 'VARIABLE' | 'OBJ_VARIABLE' | 'BIN_OP' | 'FUNC_PARAM'
  | 'RETURN' | 'EXTERNAL' | 'UNMODELLED' | 'BOUNDARIES';

/** Every `codeflow.graph.EdgeKind`. SHARED is one entry point's static write reaching another's read. */
export type EdgeKind = 'FLOW' | 'TRUE' | 'FALSE' | 'CONDITION' | 'ORDER' | 'SHARED';

export type PayloadNode = {
  id: Id;
  label: string;
  type: NodeType;
  /**
   * `file:line:col`. JsonExporter writes one on every node, boxes included - see JsonExporter.entry.
   *
   * Optional here because nothing on screen reads it yet, and the hand-written payloads in the unit
   * tests leave it out: a field every test literal has to carry and no assertion looks at is noise
   * that makes the interesting fields harder to see.
   */
  source?: string;
  /** The enclosing box. Absent on the entry method, which is the one node with no parent. */
  parent?: Id;
  /**
   * The start of `label` drawn bold - `call` in `call fetch`. JsonExporter writes it only on a
   * boundary call, so it is read as written and never found by matching the label's text.
   */
  lead?: string;
  /**
   * On a METHOD box only: the method as javac resolved it, `pkg.Class#name(ParamTypes)`. Every block
   * of one method - one per call site - carries the same string, which is what lets the condensed
   * view gather them.
   */
  method?: string;
  /** On a METHOD box only, and only when it is a boundary method's block: never found from a label. */
  boundary?: boolean;
};

export type PayloadEdge = { source: Id; target: Id; kind: EdgeKind };

/**
 * An edge with only its endpoints, which is all a walk needs.
 *
 * `neighbourhood` and `hiddenDegree` take this rather than PayloadEdge, and the narrower type is the
 * documentation: neither the direction's meaning nor the edge's kind changes what they answer.
 */
export type Link = { source: Id; target: Id };

export type Payload = {
  nodes: PayloadNode[];
  edges: PayloadEdge[];
  /**
   * file -> line number -> that line's text, trimmed: only the lines some node's `source` names.
   * Optional for the hand-written payloads in the unit tests; JsonExporter always writes it.
   */
  lines?: Record<string, Record<string, string>>;
};

/** How many edges at a node lead somewhere off screen, per direction. */
export type HiddenDegree = { in: number; out: number };

export type ViewNode = PayloadNode & {
  /** The label plus what is missing around it - `total ↑2 ↓3`. Never replaces `label`. */
  badge: string;
  /**
   * On screen. For a box: some descendant leaf is showing.
   *
   * Descendants, because a box holds boxes and one whose only showing node is a grandchild is still
   * on screen. Stated rather than derived by the renderer, so that a unit test can read it.
   */
  visible: boolean;
};

export type ViewEdge = PayloadEdge & { visible: boolean };

/**
 * One thing drawn in the condensed view: a leaf on its own, or a group standing in for every leaf of
 * it not yet taken out - `members` of them. condensed.ts decides all of it.
 */
export type CondensedNode = {
  id: Id;
  label: string;
  type: NodeType | 'GROUP';
  /** On a group: how many of its leaves it still stands for. */
  members?: number;
  /** On a group of a boundary method's blocks. */
  boundary?: boolean;
  /** On a leaf, as in the payload. */
  lead?: string;
};

/** Every payload edge of one kind between one pair of display nodes, drawn once. */
export type CondensedEdge = { source: Id; target: Id; kind: EdgeKind; count: number };

export type CondensedView = { nodes: CondensedNode[]; edges: CondensedEdge[] };

/** What a click or a hover in the condensed view lands on: a display node, or a drawn edge. */
export type CondensedTarget = { node: Id } | { edge: [Id, Id, EdgeKind] };

export type View = {
  /** Leaves on screen: everything revealed, plus one stub per offered callee. */
  showing: Set<Id>;
  /** Which of `showing` are stubs rather than genuinely revealed. */
  stubs: Set<Id>;
  hidden: Map<Id, HiddenDegree>;
  /** Every payload node, visible or not, in payload order. */
  nodes: ViewNode[];
  /** Every payload edge, visible or not, in payload order. */
  edges: ViewEdge[];
};
