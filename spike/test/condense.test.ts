import { test } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import type { Graph, Model, NodeFact } from '../src/types.ts';
import { buildModel } from '../src/load.ts';
import { condense, trimGuards } from '../src/condense.ts';

/**
 * Contraction must neither invent nor lose flows. Merging occurrences of one variable is a deliberate
 * change, so the reference is the original graph with each kept node renamed to its condensed key:
 * for every pair of distinct kept keys, reachable in the condensed graph iff in that quotient. Guard
 * edges to reads, literals, operators and calls are trimmed on purpose, so the reference omits them too.
 */
function check(m: Model) {
  const c = condense(m);
  const name = (id: string) => c.keyOf.get(id) ?? id;
  const quotient = new Map<string, Set<string>>();
  for (const e of trimGuards(m)) {
    const a = name(e.from);
    const b = name(e.to);
    if (a !== b) (quotient.get(a) ?? quotient.set(a, new Set()).get(a)!).add(b);
  }
  const condensed = new Map<string, Set<string>>();
  for (const e of c.edges) (condensed.get(e.from) ?? condensed.set(e.from, new Set()).get(e.from)!).add(e.to);
  const reach = (g: Map<string, Set<string>>, from: string) => {
    const seen = new Set<string>();
    const queue = [from];
    while (queue.length) for (const t of g.get(queue.shift()!) ?? []) if (!seen.has(t)) { seen.add(t); queue.push(t); }
    return seen;
  };
  const kept = new Set(c.keyOf.values());
  for (const k of kept) {
    const want = new Set([...reach(quotient, k)].filter((x) => kept.has(x) && x !== k));
    const got = new Set([...reach(condensed, k)].filter((x) => x !== k));
    assert.deepEqual([...got].sort(), [...want].sort(), `reachable from ${k}`);
  }
  for (const e of c.edges) assert.notEqual(e.from, e.to);
}

const node = (id: string, label: string, code: string, extra: Partial<NodeFact> = {}): NodeFact => ({
  id, label, code, file: 'A.java', line: 1, method: 'A.f', ...extra,
});

test('contraction through dropped nodes keeps flows and invents none', () => {
  const nodes = [
    node('lit', 'LITERAL', '1', { col: 1 }),
    node('x1', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 2 }),
    node('asg', 'CALL', 'x = 1', { name: '<operator>.assignment', args: ['', 'x1', 'lit'] }),
    node('x2', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 3 }),
    node('add', 'CALL', 'x + 2', { name: '<operator>.addition', col: 4 }),
    node('blk', 'BLOCK', ''),
    node('y', 'IDENTIFIER', 'y', { typeFullName: 'int', col: 5 }),
    node('other', 'LITERAL', '9', { col: 6 }),
    node('asgy', 'CALL', 'y = ...', { name: '<operator>.assignment', args: ['', 'y', 'add'] }),
  ];
  const edge = (from: string, to: string) => ({ from, to, carrier: '', kind: 'local' as const });
  const m = {
    byId: new Map(nodes.map((n) => [n.id, n])),
    methodByFullName: new Map(),
    edges: [edge('lit', 'asg'), edge('asg', 'x1'), edge('x1', 'x2'), edge('x2', 'add'), edge('add', 'blk'), edge('blk', 'y'), edge('other', 'blk')],
  } as unknown as Model;
  check(m);
  const c = condense(m);
  assert.deepEqual(c.edges.map((e) => `${e.from}>${e.to}`).sort(), [
    'A.f|BIN_OP|+|1:4>A.f|VARIABLE|y|1:5',
    'A.f|LITERAL|1|1:1>A.f|VARIABLE|x|1:2',
    'A.f|LITERAL|9|1:6>A.f|VARIABLE|y|1:5',
    'A.f|VARIABLE|x|1:2>A.f|BIN_OP|+|1:4',
  ]);
});

test('a guard to a read is dropped; to a RETURN it is kept; to a local that meets no join it is dropped', () => {
  const nodes = [
    node('cond', 'CALL', 'x > 0', { name: '<operator>.greaterThan', col: 1 }),
    node('read', 'IDENTIFIER', 'r', { typeFullName: 'int', col: 2 }),
    node('lit', 'LITERAL', '5', { col: 3 }),
    node('asg', 'CALL', 'y = 5', { name: '<operator>.assignment', args: ['', 'y', 'lit'] }),
    node('y', 'IDENTIFIER', 'y', { typeFullName: 'int', col: 4 }),
    node('ret', 'RETURN', 'return 1'),
  ];
  const guard = (to: string) => ({ from: 'cond', to, carrier: '', kind: 'guard' as const });
  const m = {
    byId: new Map(nodes.map((n) => [n.id, n])),
    methodByFullName: new Map(),
    edges: [guard('read'), guard('lit'), guard('asg'), guard('ret')],
  } as unknown as Model;
  check(m);
  assert.deepEqual(condense(m).edges.map((e) => `${e.from}>${e.to}|${e.label}`).sort(), [
    'A.f|BIN_OP|>|1:1>A.f|RETURN|if',
  ]);
});

test('a guard leaving a contracted assignment keeps the if label on the path through it', () => {
  const nodes = [
    node('a', 'IDENTIFIER', 'a', { typeFullName: 'int', col: 1 }),
    node('d', 'CALL', 'x = a', { name: '<operator>.assignment', args: ['', 'x1', 'a'], col: 2 }),
    node('b', 'RETURN', 'return a', { col: 3 }),
  ];
  const m = {
    byId: new Map(nodes.map((n) => [n.id, n])),
    methodByFullName: new Map(),
    edges: [
      { from: 'a', to: 'd', carrier: '', kind: 'local' as const },
      { from: 'd', to: 'b', carrier: '', kind: 'guard' as const },
    ],
  } as unknown as Model;
  check(m);
  assert.deepEqual(condense(m).edges.map((e) => `${e.from}>${e.to}|${e.label}`), [
    'A.f|VARIABLE|a>A.f|RETURN|if',
  ]);
});

test('each assignment of a local is its own node; reads are contracted into the reaching definition', () => {
  const nodes = [
    node('l0', 'LITERAL', '0', { line: 1, col: 1 }),
    node('r0', 'IDENTIFIER', 'r', { typeFullName: 'int', line: 1, col: 2 }),
    node('a0', 'CALL', 'r = 0', { name: '<operator>.assignment', line: 1, args: ['', 'r0', 'l0'] }),
    node('l1', 'LITERAL', '1', { line: 2, col: 1 }),
    node('r1', 'IDENTIFIER', 'r', { typeFullName: 'int', line: 2, col: 2 }),
    node('a1', 'CALL', 'r = 1', { name: '<operator>.assignment', line: 2, args: ['', 'r1', 'l1'] }),
    node('rd', 'IDENTIFIER', 'r', { typeFullName: 'int', line: 3, col: 2 }),
    node('ret', 'RETURN', 'return r', { line: 3 }),
  ];
  const edge = (from: string, to: string) => ({ from, to, carrier: '', kind: 'local' as const });
  const m = {
    byId: new Map(nodes.map((n) => [n.id, n])),
    methodByFullName: new Map(),
    edges: [edge('l0', 'r0'), edge('l1', 'r1'), edge('r1', 'rd'), edge('rd', 'ret')],
  } as unknown as Model;
  check(m);
  const c = condense(m);
  assert.deepEqual(c.nodes.map((n) => n.key).sort(), [
    'A.f|LITERAL|0|1:1', 'A.f|LITERAL|1|2:1', 'A.f|RETURN', 'A.f|VARIABLE|r|1:2', 'A.f|VARIABLE|r|2:2',
  ]);
  assert.deepEqual(c.edges.map((e) => `${e.from}>${e.to}`).sort(), [
    'A.f|LITERAL|0|1:1>A.f|VARIABLE|r|1:2',
    'A.f|LITERAL|1|2:1>A.f|VARIABLE|r|2:2',
    'A.f|VARIABLE|r|2:2>A.f|RETURN',
  ]);
});

const local = (from: string, to: string) => ({ from, to, carrier: '', kind: 'local' as const });
const guardTo = (to: string, from = 'cond') => ({ from, to, carrier: '', kind: 'guard' as const });
const mk = (nodes: NodeFact[], edges: unknown[]) => ({
  byId: new Map(nodes.map((n) => [n.id, n])), methodByFullName: new Map(), edges,
}) as unknown as Model;
const twoDefs = () => [
  node('l1', 'LITERAL', '1', { col: 1 }),
  node('x1', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 2 }),
  node('a1', 'CALL', 'x = 1', { name: '<operator>.assignment', args: ['', 'x1', 'l1'] }),
  node('l2', 'LITERAL', '2', { col: 3 }),
  node('x2', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 4 }),
  node('a2', 'CALL', 'x = 2', { name: '<operator>.assignment', args: ['', 'x2', 'l2'] }),
  node('cond', 'CALL', 'c > 0', { name: '<operator>.greaterThan', col: 5 }),
];

test('two definitions reaching one read meet in an if join; a guard to a definition points at the join', () => {
  const nodes = [
    ...twoDefs(),
    node('rd', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 6 }),
    node('y', 'IDENTIFIER', 'y', { typeFullName: 'int', col: 7 }),
    node('ay', 'CALL', 'y = x', { name: '<operator>.assignment', args: ['', 'y', 'rd'] }),
  ];
  const m = mk(nodes, [local('l1', 'x1'), local('l2', 'x2'), local('x1', 'rd'), local('x2', 'rd'), local('rd', 'ay'), local('ay', 'y'), guardTo('a2')]);
  check(m);
  const c = condense(m);
  assert.deepEqual(c.nodes.filter((n) => n.label === 'if').map((n) => [n.key, n.kind]), [['A.f|JOIN|x1,x2', 'BIN_OP']]);
  assert.deepEqual(c.edges.map((e) => `${e.from}>${e.to}|${e.label}`).sort(), [
    'A.f|BIN_OP|>|1:5>A.f|JOIN|x1,x2|if',
    'A.f|JOIN|x1,x2>A.f|VARIABLE|y|1:7|',
    'A.f|LITERAL|1|1:1>A.f|VARIABLE|x|1:2|',
    'A.f|LITERAL|2|1:3>A.f|VARIABLE|x|1:4|',
    'A.f|VARIABLE|x|1:2>A.f|JOIN|x1,x2|',
    'A.f|VARIABLE|x|1:4>A.f|JOIN|x1,x2|',
  ]);
});

test('two reads of the same merge share one join', () => {
  const nodes = [
    ...twoDefs(),
    node('rd1', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 6 }),
    node('rd2', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 7 }),
    node('ret', 'RETURN', 'return x + x'),
  ];
  const m = mk(nodes, [local('x1', 'rd1'), local('x2', 'rd1'), local('x1', 'rd2'), local('x2', 'rd2'), local('rd1', 'ret'), local('rd2', 'ret')]);
  check(m);
  assert.equal(condense(m).nodes.filter((n) => n.label === 'if').length, 1);
});

test('a guard to a definition that reaches no join is dropped', () => {
  const nodes = [...twoDefs(), node('rd', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 6 }), node('ret', 'RETURN', 'return x')];
  const m = mk(nodes, [local('x2', 'rd'), local('rd', 'ret'), guardTo('a2'), guardTo('x1')]);
  check(m);
  const c = condense(m);
  assert.equal(c.nodes.filter((n) => n.label === 'if').length, 0);
  assert.deepEqual(c.edges.map((e) => `${e.from}>${e.to}|${e.label}`), ['A.f|VARIABLE|x|1:4>A.f|RETURN|']);
});

test('a guard to a definition reaches the join through a plain read identifier', () => {
  const nodes = [
    ...twoDefs(),
    node('rcv', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 8 }),
    node('rd', 'IDENTIFIER', 'x', { typeFullName: 'int', col: 6 }),
    node('ret', 'RETURN', 'return x'),
  ];
  const m = mk(nodes, [local('x1', 'rd'), local('x2', 'rcv'), local('rcv', 'rd'), local('rd', 'ret'), guardTo('a2')]);
  check(m);
  assert.ok(condense(m).edges.some((e) => e.label === 'if' && e.from.includes('BIN_OP') && e.to.includes('JOIN')));
});

const resources = new URL('../../app/src/test/resources/', import.meta.url);
test('contraction property holds on every committed case dump', () => {
  let cases = 0;
  for (const name of readdirSync(resources)) {
    const file = new URL(`${name}/spike-graph.json`, resources);
    if (!existsSync(file)) continue;
    check(buildModel(JSON.parse(readFileSync(file, 'utf8')) as Graph));
    cases++;
  }
  assert.ok(cases >= 70);
});
