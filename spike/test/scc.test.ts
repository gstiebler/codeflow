import { test } from 'node:test';
import assert from 'node:assert/strict';
import { hand, KEY } from './corpus.ts';
import { buildModel } from '../src/load.ts';
import { condense, size } from '../src/scc.ts';

const m = buildModel(hand());
const c = condense(m);

/** The components the nodes on one source line landed in. The graph is nodes; the tests read lines. */
const componentsAt = (stmt: string) =>
  new Set([...m.stmtOfNode].filter(([, at]) => at === stmt).map(([id]) => c.componentOf.get(id)!));

/** The lines a component spans, which is what a reader would be shown for it. */
const linesOf = (component: string) =>
  [...new Set((c.members.get(component) ?? []).map((id) => m.stmtOfNode.get(id)!))].sort();

test('the two statements that feed each other collapse into one component', () => {
  const spanning = [...c.members.keys()].filter((id) => linesOf(id).length > 1);
  assert.equal(spanning.length, 1);
  assert.deepEqual(linesOf(spanning[0]!), [KEY.spinA, KEY.spinB]);
  assert.ok(size(c, spanning[0]!) > 1);
});

test('nothing else in the fixture is a cycle', () => {
  const spanning = [...c.members.keys()].filter((id) => linesOf(id).length > 1);
  assert.equal(spanning.length, 1, `unexpected cycles: ${spanning.map(linesOf).join(' | ')}`);
});

test('a component is named after one of its own nodes, so a report can locate it', () => {
  for (const [id, group] of c.members) assert.ok(group.includes(id));
});

test('collapsing removes the edges inside the cycle and keeps the rest', () => {
  assert.ok(!c.edges.some((e) => e.from === e.to));
  const fieldHop = c.edges.filter((e) => e.kind === 'field');
  assert.deepEqual(fieldHop.map((e) => `${m.stmtOfNode.get(e.from)} -> ${m.stmtOfNode.get(e.to)}`), [
    `${KEY.fieldWrite} -> ${KEY.fieldRead}`,
  ]);
});

test('a chain that is not a cycle is not collapsed', () => {
  const shared = [...componentsAt(KEY.endpoint)].filter((x) => componentsAt(KEY.findById).has(x));
  assert.deepEqual(shared, []);
});

test('a deep chain does not overflow the stack', () => {
  const g = hand();
  const method = 'app.svc.Svc.spin:void()';
  for (let i = 0; i < 20000; i += 1) {
    g.nodes.push({ id: `d${i}`, label: 'IDENTIFIER', code: `v${i}`, file: 'deep/D.java', line: i + 1, method });
    if (i > 0) g.edges.push({ kind: 'rd', from: `d${i - 1}`, to: `d${i}` });
  }
  const deep = condense(buildModel(g));
  for (let i = 0; i < 20000; i += 1) assert.equal(deep.componentOf.get(`d${i}`), `d${i}`);
});
