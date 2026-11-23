/**
 * The payload's shape and the view's, in one place.
 *
 * JsonExporter writes the payload and is the authority on it; nothing here may add a field. The
 * condensed view is what the renderer is handed, and every field on it is a decision condensed.ts has
 * already made - so a renderer reaching past this type for something it could work out itself is the
 * boundary going soft.
 */

export type Id = string;

/**
 * Every type JsonExporter emits: `codeflow.graph.NodeType`, plus METHOD for a block.
 *
 * METHOD is a block of one method (condensed.ts gathers them into groups) and everything else is a
 * leaf. That distinction is the one the whole viewer turns on, so it is spelled out here rather than
 * left to a string comparison in each renderer.
 */
export type NodeType =
  | 'METHOD'
  | 'BASE' | 'LITERAL' | 'VARIABLE' | 'OBJ_VARIABLE' | 'BIN_OP' | 'FUNC_PARAM'
  | 'RETURN' | 'EXTERNAL' | 'UNMODELLED' | 'BOUNDARIES' | 'EVENT' | 'THROW';

/** Zoom levels supported by the viewer and `--level`. */
export type ZoomLevel = 'architecture' | 'workflow' | 'expressions';

/** Compact per-method effect summary emitted by JsonExporter on METHOD boxes. */
export type MethodSummary = {
  reads: string[];
  writes: string[];
  boundaries: string[];
  throws: string[];
  gaps: number;
};

/**
 * Every `codeflow.graph.EdgeKind`. SHARED is one entry point's static write reaching another's read;
 * EVENT is an entry point's event starting a node nothing else feeds.
 */
export type EdgeKind = 'FLOW' | 'TRUE' | 'FALSE' | 'CONDITION' | 'ORDER' | 'SHARED' | 'EVENT';

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
  /** On a METHOD box only: true when entered via `--assume-single-impl` DI dispatch. */
  assumed?: boolean;
  /** Semantic class role on a METHOD box (`CONTROLLER`, `SERVICE`, `REPOSITORY`, `ENTITY`). */
  classRole?: string;
  /** Semantic boundary role on a METHOD box (`DB`, `HTTP`, `EVENT`, `NOISE`). */
  boundaryRole?: string;
  /** Annotated entrypoint caption on a root METHOD box (`POST /loans/{id}/repay`, `SCHEDULED poll`). */
  entry?: string;
  /** Compact effect summary on a METHOD box. */
  summary?: MethodSummary;
  /** Qualified declaring class name on a field node (`pkg.Loan`). */
  fieldOwner?: string;
  /** Field access operation on a field node (`read`, `write`, `join`). */
  fieldOp?: string;
  /** Semantic call role on an EXTERNAL node (`DB`, `HTTP`, `EVENT`, `NOISE`). */
  role?: string;
  /**
   * On a METHOD box only: `file:line:col` of the call that opened this block. Absent on a root, which
   * nothing called. The boxes nest by call, so walking `parent` and reading this is the call stack.
   */
  called?: string;
};

export type PayloadEdge = { source: Id; target: Id; kind: EdgeKind };

export type Payload = {
  /** Initial zoom level requested via `--level`, or undefined (defaults to `expressions`). */
  initialLevel?: ZoomLevel;
  nodes: PayloadNode[];
  edges: PayloadEdge[];
  /**
   * file -> line number -> that line's text, trimmed: only the lines some node's `source` names.
   * Optional for the hand-written payloads in the unit tests; JsonExporter always writes it.
   */
  lines?: Record<string, Record<string, string>>;
};

/**
 * One thing drawn: a leaf on its own, a group standing in for hidden leaves (`members`), or a
 * high-level card (`ENTITY` state card, `DECISION` contracted predicate, or summary `GROUP`).
 */
export type CondensedNode = {
  id: Id;
  label: string;
  type: NodeType | 'GROUP' | 'ENTITY' | 'DECISION';
  /** On a group: how many of its leaves it still stands for. */
  members?: number;
  /** On a group whose method box is a boundary method's block. */
  boundary?: boolean;
  /** On a group/card entered via assumed single-implementation DI dispatch. */
  assumed?: boolean;
  /** Semantic role badge (`DB`, `HTTP`, `EVENT`, `SERVICE`, `CONTROLLER`, `ENTITY`, etc.). */
  role?: string;
  /** Compact multi-line effect details rendered on Summary Cards and Entity State Cards. */
  subtitle?: string[];
  /** On a leaf, as in the payload. */
  lead?: string;
  /**
   * The frame this node is drawn inside: a revealed leaf its method box's, and the group of an open box
   * (its rest node) its own. Absent on `program` and on a box with no revealed leaf.
   */
  frame?: Id;
};

/**
 * The outline a method's revealed values are drawn inside: one per open box, `frame:<box id>`. Not a
 * node - no edge ends on it - and never inside another frame, so it holds only leaves and its own rest node.
 */
export type CondensedFrame = {
  id: Id;
  /** The method box it stands for. */
  box: Id;
  /** The box's caption: the method's simple name. */
  label: string;
  /** The box is a boundary method's block. */
  boundary?: true;
  /** The box was entered via assumed single-implementation DI dispatch. */
  assumed?: true;
};

/**
 * Every payload edge between one ordered pair of display nodes, whatever its kind, drawn once:
 * `count` of them in all, `kinds` how many of each.
 *
 * In Workflow and Architecture views, `caption` carries a derived label (e.g. contracted expression
 * or field effect) and `contractedIds` lists the collapsed intermediate expression node IDs that
 * clicking the edge expands.
 */
export type CondensedEdge = {
  source: Id;
  target: Id;
  kinds: Partial<Record<EdgeKind, number>>;
  count: number;
  caption?: string;
  contractedIds?: Id[];
};

export type CondensedView = { nodes: CondensedNode[]; edges: CondensedEdge[]; frames: CondensedFrame[] };

/** What a click or a hover in the condensed view lands on: a display node, or a drawn edge. */
export type CondensedTarget = { node: Id } | { edge: [Id, Id] };
