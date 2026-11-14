import type { ValueEdge, Model, NodeFact } from './types.ts';

/**
 * A condensed view of `m.edges`, in the style of the Kotlin engine's truth.md: variables collapse to
 * one node per (method, name), a method's returns to one node, fields to one node per member; calls
 * into the corpus, assignments, `this`, type references and class-name receivers disappear. A dropped
 * node is contracted: each predecessor is connected to each successor, through chains of dropped
 * nodes. Pure view; the analysis in load.ts is untouched.
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

/**
 * Guard edges run from a condition to every node evaluated in the branch it controls. Only the writes
 * the condition decides are worth drawing: the defined variable or field of an assignment (Joern
 * targets the assignment call, which maps to its left-hand side) and a RETURN. Reads, literals,
 * operators and calls are dropped. Other edges pass through untouched.
 */
export function trimGuards(m: Model): ValueEdge[] {
  const isAssignment = (n: NodeFact) => n.label === 'CALL' && !!n.name?.startsWith('<operator>.assignment');
  const lhsOf = (n: NodeFact) => n.args?.find(Boolean); // args[0] is the empty receiver slot
  const defined = new Set<string>();
  for (const n of m.byId.values()) if (isAssignment(n) && lhsOf(n)) defined.add(lhsOf(n)!);
  return m.edges.flatMap((e) => {
    if (e.kind !== 'guard') return [e];
    const t = m.byId.get(e.to)!;
    if (t.label === 'RETURN' || defined.has(t.id)) return [e];
    if (isAssignment(t) && lhsOf(t)) return [{ ...e, to: lhsOf(t)! }];
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
          c = { key: `${n.method}|VARIABLE|${n.code}`, kind, label: n.code, method: n.method };
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
    const walk = (from: string, label: string, first: boolean) => {
      for (const e of out.get(from) ?? []) {
        const l = first ? (e.guard ? 'if' : '') : label;
        const t = info.get(e.to);
        if (t) {
          if (t.key !== c.key) edges.add(JSON.stringify([c.key, t.key, l]));
        } else if (!seen.has(`${e.to}|${l}`)) {
          seen.add(`${e.to}|${l}`);
          walk(e.to, l, false);
        }
      }
    };
    walk(id, '', true);
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
