import { test } from 'node:test';
import assert from 'node:assert/strict';
import { condensed, condensedOpening, tapChoices } from '../../../main/resources/viewer/condensed.ts';
import type { EdgeKind, Payload, PayloadNode } from '../../../main/resources/viewer/types.ts';

const box = (id: string, label: string, method: string, parent?: string): PayloadNode =>
  ({ id, label, type: 'METHOD', method, ...(parent ? { parent } : {}) });
const leaf = (id: string, label: string, parent: string, type: PayloadNode['type'] = 'VARIABLE'): PayloadNode =>
  ({ id, label, type, source: `p/A.java:1:1`, parent });
const edge = (source: string, target: string, kind: EdgeKind = 'FLOW') => ({ source, target, kind });

/**
 * `main` (R): the event, a return, two parameters (`p1`, `p2`) and a value `v`. The event starts `p1`, `v`
 * (both in R) and `c` (in the callee box C, hidden in `program`).
 */
const ENTRY: Payload = {
  nodes: [
    box('R', 'main', 'p.A#main()'),
    leaf('e', 'event', 'R', 'EVENT'), leaf('ret', 'return', 'R', 'RETURN'),
    leaf('p1', 'a', 'R', 'FUNC_PARAM'), leaf('v', 'v', 'R'), leaf('p2', 'b', 'R', 'FUNC_PARAM'),
    box('C', 'f', 'p.A#f()', 'R'), leaf('c', 'c', 'C'), leaf('d', 'd', 'C'),
  ],
  edges: [edge('e', 'p1', 'EVENT'), edge('e', 'v', 'EVENT'), edge('e', 'p2', 'EVENT'), edge('e', 'c', 'EVENT'), edge('v', 'ret')],
};

test('the opening is the event and the entry return: no parameter', () => {
  assert.deepEqual([...condensedOpening(ENTRY)].sort(), ['e', 'ret']);
});

test('an EVENT edge into a leaf hidden in program is not drawn; into a leaf hidden in the event\'s own box it is, once', () => {
  const view = condensed(ENTRY, condensedOpening(ENTRY));
  // `e -> c` (c is in program) is gone; e -> p1, v, p2 are one edge to the rest node, counted three times.
  assert.deepEqual(view.edges.filter((e) => e.source === 'e'), [{ source: 'e', target: 'R', kinds: { EVENT: 3 }, count: 3 }]);
  assert.deepEqual(view.nodes.map((n) => [n.id, n.type === 'GROUP' ? n.members : 0]), [['program', 2], ['R', 3], ['e', 0], ['ret', 0]]);
});

test('an EVENT edge is drawn once its target is revealed, whichever box it sits in', () => {
  const view = condensed(ENTRY, new Set(['e', 'ret', 'c']));
  assert.deepEqual(view.edges.filter((e) => e.source === 'e').map((e) => [e.target, e.count]), [['R', 3], ['c', 1]]);
});

test('an EVENT edge into a callee box that is open but whose target is hidden is not drawn', () => {
  // `d` revealed opens C; `c` stays hidden there, in C's group, and e -> c is still not drawn.
  const view = condensed(ENTRY, new Set(['e', 'ret', 'd']));
  assert.ok(view.nodes.some((n) => n.id === 'C'));
  assert.deepEqual(view.edges.filter((e) => e.source === 'e').map((e) => e.target), ['R']);
});

test('clicking the event\'s edge lists the parameters first, then the values, in payload order', () => {
  const choices = tapChoices(ENTRY, condensedOpening(ENTRY), { edge: ['e', 'R'] });
  assert.deepEqual(choices.map((c) => `${c.section}:${c.id}`), ['parameters:p1', 'parameters:p2', 'values:v']);
});

test('a group\'s frontier ignores the edges that are not drawn', () => {
  // `c` has only the undrawn EVENT edge, `d` flows into the entry box: only `d` is on program's frontier.
  const p: Payload = { ...ENTRY, edges: [...ENTRY.edges, edge('d', 'v')] };
  const values = tapChoices(p, condensedOpening(p), { node: 'program' }).filter((c) => c.section === 'values');
  assert.deepEqual(values.map((c) => c.id), ['d']);
  // With nothing else drawn, a click still has something to show: every member.
  const alone = tapChoices(ENTRY, condensedOpening(ENTRY), { node: 'program' }).filter((c) => c.section === 'values');
  assert.deepEqual(alone.map((c) => c.id), ['c', 'd']);
});
