/**
 * The contract between Joern and everything downstream.
 *
 * `spike/joern/export.sc` produces exactly this shape and nothing else - no filtering, no typing, no
 * analysis. That split is the whole point of the spike's layout: a Joern script costs two and a half
 * minutes to run and cannot be unit tested, so the only thing it is allowed to decide is what to
 * copy out. Every judgement lives in TypeScript, runs in milliseconds, and is tested against
 * `spike/test/corpus.ts` - a hand-written Graph that owes Joern nothing.
 */

/** One annotation as it appears in source. `value` is the single unnamed argument, if there is one. */
export type Ann = { name: string; value?: string };

export type MemberFact = {
  name: string;
  type: string;
  annotations: Ann[];
};

export type TypeFact = {
  /** Fully qualified, e.g. `org.apache.fineract.portfolio.workingcapitalloan.domain.WorkingCapitalLoan`. */
  fullName: string;
  /** Simple name. Carried separately because Lombok accessors on unresolved receivers are matched by
   * simple name - the namespace is exactly what no-delombok parsing loses. */
  name: string;
  file: string;
  annotations: Ann[];
  members: MemberFact[];
};

export type ParamFact = {
  id: string;
  name: string;
  /** 1-based, matching Joern. Index 0 is the receiver (`this`), which is never a parameter here. */
  index: number;
  line: number;
  type: string;
};

export type MethodFact = {
  fullName: string;
  /** Simple name. The fingerprint keys off this rather than `fullName`, so a method that moves to
   * another class still matches itself. */
  name: string;
  typeName: string;
  typeFullName: string;
  file: string;
  line: number;
  annotations: Ann[];
  params: ParamFact[];
  /** Node ids of this method's RETURN nodes. */
  returns: string[];
};

export type NodeFact = {
  id: string;
  /** Joern's node label: CALL, IDENTIFIER, METHOD_PARAMETER_IN, FIELD_IDENTIFIER, RETURN, LITERAL, ... */
  label: string;
  code: string;
  file: string;
  line: number;
  /** `fullName` of the enclosing method. */
  method: string;
  /** CALL only: the callee's simple name. */
  name?: string;
  /** CALL only: the resolved callee, or a `<unresolved...>` marker. */
  methodFullName?: string;
  /** CALL only: the static type of argument 0, i.e. what the call is made on. */
  receiverType?: string;
  /** CALL only: node ids of the arguments, argument 0 first. */
  args?: string[];
  /** Field accesses only: `SimpleTypeName.memberName`, when Joern resolved the member. */
  member?: string;
  typeFullName?: string;
};

export type EdgeFact = {
  kind: 'rd' | 'cdg';
  from: string;
  to: string;
};

export type Graph = {
  version: 1;
  /** The commit the sources were read from. Printed in reports; never parsed. */
  commit: string;
  types: TypeFact[];
  methods: MethodFact[];
  nodes: NodeFact[];
  edges: EdgeFact[];
};

/** A statement is a source line. See `load.ts` for why that is the right grain here. */
export type Stmt = {
  /** `file:line`. Unique within a Graph, and the id every later stage passes around. */
  key: string;
  file: string;
  line: number;
  /** The widest piece of source Joern attributed to this line. */
  code: string;
  method: string;
};

export type EdgeKind = 'local' | 'field' | 'arg' | 'ret' | 'guard';

export type ValueEdge = {
  from: string;
  to: string;
  /** What carries the value across this edge: a variable, `field Type.name`, `arg n`, `return`, or
   * the guard's condition. This is the column a call hierarchy cannot show, and the reason the
   * rate-factor bug was hard to see: writer and reader were joined by a field, not by a call. */
  carrier: string;
  kind: EdgeKind;
};

export type Model = {
  stmts: Map<string, Stmt>;
  edges: ValueEdge[];
  /** Node id to NodeFact, and node id to statement key. */
  byId: Map<string, NodeFact>;
  stmtOfNode: Map<string, string>;
  methodByFullName: Map<string, MethodFact>;
  typeByName: Map<string, TypeFact>;
  graph: Graph;
};

export type BoundaryKind = 'net' | 'db' | 'throw' | 'json';

export type Boundary = {
  /** Stable across versions: the label plus the direction. Never contains a line number. */
  key: string;
  stmt: string;
  dir: 'in' | 'out';
  kind: BoundaryKind;
  label: string;
};
