import type { ValueEdge, Model, NodeFact } from './types.ts';

/**
 * A condensed view of `m.edges`, in the style of the Kotlin engine's truth.md: each assignment of a local is
 * one node (reads are contracted into the definitions that reach them), a method's returns to one node, fields to one node per member; calls
 * into the corpus, assignments, `this`, type references and class-name receivers disappear. A dropped
 * node is contracted: each predecessor is connected to each successor, through chains of dropped
 * nodes. Where two or more definitions reach one read, the read becomes a join node labelled `if`
 * (kind BIN_OP), as in the Kotlin engine: definitions point at the join, the join at the consumers, and
 * the conditions that choose between the definitions point at the join. Pure view; the analysis in
 * load.ts is untouched.
 */

export type Kind = 'LITERAL' | 'VARIABLE' | 'OBJ_VARIABLE' | 'BIN_OP' | 'FUNC_PARAM' | 'RETURN' | 'EXTERNAL';

export type CNode = { key: string; kind: Kind; label: string; method?: string };
/** `label` is `if` for a guard edge, otherwise empty. */
export type CEdge = { from: string; to: string; label: string };

const OPERATORS: Record<string, string> = {
  addition: '+', subtraction: '-', multiplication: '*', division: 'div', modulo: '%',
  minus: '-', plus: '+', equals: '==', notEquals: '!=', lessThan: '<', greaterThan: '>',
  lessEqualsThan: '<=', greaterEqualsThan: '>=', logicalAnd: '&&', logicalOr: '||', logicalNot: '!',
  conditional: 'ternary', assignmentPlus: '+=', assignmentMinus: '-=', assignmentMultiplication: '*=',
  assignmentDivision: 'div=', postIncrement: '++', preIncrement: '++', postDecrement: '--', preDecrement: '--',
};

const SIMPLE = new Set(['int', 'long', 'short', 'byte', 'char', 'boolean', 'float', 'double', 'java.lang.String']);

/** `field` nodes are keyed without a method and sort after every method key. */
const FIELD_PREFIX = '~field|';

/** Identifiers that define a local: assignment targets and ++/-- operands. */
function definitions(m: Model): Set<string> {
  const defIds = new Set<string>();
  for (const n of m.byId.values()) {
    if (n.label !== 'CALL' || !n.name) continue;
    const isDef = n.name.startsWith('<operator>.assignment') || /^<operator>\.(post|pre)(Increment|Decrement)$/.test(n.name);
    const target = isDef ? n.args?.find(Boolean) : undefined;
    if (target) defIds.add(target);
  }
  return defIds;
}

/**
 * Join reads: local reads (not definitions, parameters, `this` or class receivers) reached by two or
 * more distinct identifiers. Maps the read to its sorted source ids. Such a read is where competing
 * definitions meet.
 */
function joinReads(m: Model, defIds: Set<string>): Map<string, string[]> {
  const sources = new Map<string, Set<string>>();
  for (const e of m.edges) {
    if (e.kind !== 'local' || m.byId.get(e.from)?.label !== 'IDENTIFIER') continue;
    (sources.get(e.to) ?? sources.set(e.to, new Set()).get(e.to)!).add(e.from);
  }
  const joins = new Map<string, string[]>();
  for (const [id, from] of sources) {
    const n = m.byId.get(id);
    if (n?.label !== 'IDENTIFIER' || from.size < 2 || defIds.has(id) || n.code === 'this') continue;
    if (n.typeFullName?.replace(/^.*\./, '') === n.code) continue; // class receiver
    if ((m.methodByFullName.get(n.method)?.params ?? []).some((p) => p.index > 0 && p.name === n.code)) continue;
    joins.set(id, [...from].sort());
  }
  return joins;
}

/**
 * Guard edges run from a condition to every node evaluated in the branch it controls. Only the writes
 * the condition decides are worth drawing: a RETURN, a field or array element written by an assignment,
 * and, for a local, the join where its definition meets others (Joern targets the assignment call, which
 * maps to its left-hand side). A guard to a local definition is replaced by guards to the join reads that
 * definition reaches (through plain read identifiers); if it reaches none, the condition chooses nothing and the guard is dropped.
 * Reads, literals, operators and calls are dropped. Other edges pass through untouched.
 */
export function trimGuards(m: Model): ValueEdge[] {
  const isAssignment = (n: NodeFact) => n.label === 'CALL' && !!n.name?.startsWith('<operator>.assignment');
  const lhsOf = (n: NodeFact) => n.args?.find(Boolean); // args[0] is the empty receiver slot
  const defIds = definitions(m);
  const joins = joinReads(m, defIds);
  const next = new Map<string, string[]>();
  for (const e of m.edges) if (e.kind === 'local') (next.get(e.from) ?? next.set(e.from, []).get(e.from)!).push(e.to);
  // Join reads a definition reaches, through plain read identifiers (Joern's receiver of `new T()`); stops at joins and other defs.
  const reaches = (def: string): string[] => {
    const seen = new Set([def]);
    const found: string[] = [];
    const queue = [def];
    while (queue.length) {
      for (const t of next.get(queue.shift()!) ?? []) {
        if (seen.has(t)) continue;
        seen.add(t);
        if (joins.has(t)) found.push(t);
        else if (!defIds.has(t) && m.byId.get(t)?.label === 'IDENTIFIER') queue.push(t);
      }
    }
    return found;
  };
  const toDef = (e: ValueEdge, def: string): ValueEdge[] => reaches(def).map((to) => ({ ...e, to }));
  return m.edges.flatMap((e) => {
    if (e.kind !== 'guard') return [e];
    const t = m.byId.get(e.to)!;
    if (t.label === 'RETURN') return [e];
    if (defIds.has(t.id)) return toDef(e, t.id);
    if (isAssignment(t) && lhsOf(t)) return defIds.has(lhsOf(t)!) ? toDef(e, lhsOf(t)!) : [{ ...e, to: lhsOf(t)! }];
    return [];
  });
}

export function condense(m: Model): { nodes: CNode[]; edges: CEdge[]; keyOf: Map<string, string> } {
  const { byId } = m;
  const shortName = (method: string) => m.methodByFullName.get(method)?.name ?? method.replace(/:.*$/, '').replace(/^.*\./, '');
  const paramNames = (method: string) => new Set((m.methodByFullName.get(method)?.params ?? []).filter((p) => p.index > 0).map((p) => p.name));

  // Pass 1: which original node becomes which condensed node, if any.
  const info = new Map<string, CNode>();
  const used = new Map<string, number>();
  const occurrence = (n: NodeFact, kind: Kind, label: string) => {
    const base = `${n.method}|${kind}|${label}|${n.line}:${n.col ?? 0}`;
    const k = (used.get(base) ?? 0) + 1;
    used.set(base, k);
    return { key: k === 1 ? base : `${base}#${k}`, kind, label, method: n.method };
  };
  // Identifiers that define a local, those with a reaching definition, and joins of several definitions.
  const defIds = definitions(m);
  const joins = joinReads(m, defIds);
  const reached = new Set<string>();
  for (const e of m.edges) if (e.kind === 'local') reached.add(e.to);
  for (const n of byId.values()) {
    let c: CNode | undefined;
    if (n.label === 'LITERAL') c = occurrence(n, 'LITERAL', n.code);
    else if (n.label === 'RETURN') c = { key: `${n.method}|RETURN`, kind: 'RETURN', label: shortName(n.method), method: n.method };
    else if (n.label === 'METHOD_REF') c = occurrence(n, 'EXTERNAL', n.code.replace(/\s+/g, ' ').slice(0, 40));
    else if (n.label === 'METHOD_PARAMETER_IN') {
      if (n.name !== 'this' && n.code !== 'this') {
        const name = n.code.replace(/^.*[\s.]/, '');
        c = { key: `${n.method}|FUNC_PARAM|${name}`, kind: 'FUNC_PARAM', label: name, method: n.method };
      }
    } else if (n.label === 'IDENTIFIER') {
      const classReceiver = n.typeFullName?.replace(/^.*\./, '') === n.code;
      if (n.code !== 'this' && !classReceiver) {
        if (paramNames(n.method).has(n.code)) c = { key: `${n.method}|FUNC_PARAM|${n.code}`, kind: 'FUNC_PARAM', label: n.code, method: n.method };
        else {
          const kind = SIMPLE.has(n.typeFullName ?? '') ? 'VARIABLE' : 'OBJ_VARIABLE';
          if (defIds.has(n.id)) c = occurrence(n, kind, n.code);
          else if (joins.has(n.id)) c = { key: `${n.method}|JOIN|${joins.get(n.id)!.join(',')}`, kind: 'BIN_OP', label: 'if', method: n.method };
          else if (reached.has(n.id)) c = undefined; // a read: contracted into its reaching definition
          else c = { key: `${n.method}|VARIABLE|${n.code}`, kind, label: n.code, method: n.method };
        }
      }
    } else if (n.label === 'CALL' && n.name) {
      if (n.name === '<operator>.assignment') c = undefined;
      else if (n.name === '<operator>.fieldAccess') {
        const name = n.member?.replace(/^.*\./, '') ?? (n.args ?? []).map((a) => byId.get(a)).find((a) => a?.label === 'FIELD_IDENTIFIER')?.code;
        if (name) c = { key: `${FIELD_PREFIX}${n.member ?? name}`, kind: 'VARIABLE', label: name };
      } else if (n.name.startsWith('<operator>.')) {
        const op = n.name.slice('<operator>.'.length);
        // `new App()` is an alloc plus an `<init>`; Kotlin shows it as an external node named after the type.
        c = op === 'alloc' ? occurrence(n, 'EXTERNAL', n.typeFullName?.replace(/^.*\./, '') ?? op) : occurrence(n, 'BIN_OP', OPERATORS[op] ?? op);
      } else if (!m.methodByFullName.has(n.methodFullName ?? '')) c = occurrence(n, 'EXTERNAL', n.name);
    }
    if (c) info.set(n.id, c);
  }

  // Pass 2: contract dropped nodes.
  const out = new Map<string, { to: string; guard: boolean }[]>();
  for (const e of trimGuards(m)) (out.get(e.from) ?? out.set(e.from, []).get(e.from)!).push({ to: e.to, guard: e.kind === 'guard' });
  const edges = new Set<string>();
  for (const [id, c] of info) {
    const seen = new Set<string>();
    const walk = (from: string, label: string) => {
      for (const e of out.get(from) ?? []) {
        const l = e.guard ? 'if' : label; // a path is `if` once any hop on it is a guard
        const t = info.get(e.to);
        if (t) {
          if (t.key !== c.key) edges.add(JSON.stringify([c.key, t.key, l]));
        } else if (!seen.has(`${e.to}|${l}`)) {
          seen.add(`${e.to}|${l}`);
          walk(e.to, l);
        }
      }
    };
    walk(id, '');
  }
  const nodes = new Map([...info.values()].map((c) => [c.key, c]));
  // The same pair as both a plain and an `if` edge keeps the plain one.
  for (const k of [...edges]) {
    const [from, to, label] = JSON.parse(k) as [string, string, string];
    if (label === 'if' && edges.has(JSON.stringify([from, to, ''])) ) edges.delete(k);
  }
  return {
    keyOf: new Map([...info].map(([id, c]) => [id, c.key])),
    nodes: [...nodes.values()],
    edges: [...edges].map((s) => JSON.parse(s) as [string, string, string]).map(([from, to, label]) => ({ from, to, label })),
  };
}
