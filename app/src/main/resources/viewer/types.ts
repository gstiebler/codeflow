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
  | 'RETURN' | 'MEM_SPACE' | 'EXTERNAL' | 'UNMODELLED';

export type EdgeKind = 'FLOW' | 'TRUE' | 'FALSE' | 'CONDITION';

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
};

export type PayloadEdge = { source: Id; target: Id; kind: EdgeKind };

/**
 * An edge with only its endpoints, which is all a walk needs.
 *
 * `neighbourhood` and `hiddenDegree` take this rather than PayloadEdge, and the narrower type is the
 * documentation: neither the direction's meaning nor the edge's kind changes what they answer.
 */
export type Link = { source: Id; target: Id };

export type Payload = { nodes: PayloadNode[]; edges: PayloadEdge[] };

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
