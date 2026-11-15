import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  PROGRAM, condensed, condensedOpening, groupOf, hoverText, tapCondensed,
} from '../../../main/resources/viewer/condensed.ts';
import type { CondensedView, EdgeKind, Payload, PayloadNode } from '../../../main/resources/viewer/types.ts';

const FETCH = 'boundary:p.Gw#fetch(int)';
const SAVE = 'boundary:p.Gw#save(int)';

const box = (id: string, label: string, method: string, parent?: string, boundary = false): PayloadNode =>
  ({ id, label, type: 'METHOD', method, ...(parent ? { parent } : {}), ...(boundary ? { boundary } : {}) });
const leaf = (id: string, label: string, parent: string | undefined, source: string, type: PayloadNode['type'] = 'VARIABLE'): PayloadNode =>
  ({ id, label, type, source, ...(parent ? { parent } : {}) });
const edge = (source: string, target: string, kind: EdgeKind = 'FLOW') => ({ source, target, kind });

/**
 * `main` calls the boundary `fetch` twice and `save` once, and `helper`, which calls `fetch` again -
 * so `fetch` has three blocks at three call sites, one nested in a non-boundary box. `n10` has no box
 * at all. `c` and `n10` touch nothing.
 */
const PAYLOAD: Payload = {
  nodes: [
    box('b0', 'main', 'p.App#main()'),
    leaf('n1', 'a', 'b0', 'p/App.java:5:9'),
    leaf('n2', 'b', 'b0', 'p/App.java:6:9'),
    leaf('n3', 'c', 'b0', 'p/App.java:7:9'),
    box('b1', 'fetch', 'p.Gw#fetch(int)', 'b0', true),
    leaf('n4', 'x', 'b1', 'p/Gw.java:3:22', 'FUNC_PARAM'),
    { ...leaf('n5', 'call fetch', 'b1', 'p/Gw.java:3:5', 'EXTERNAL'), lead: 'call' },
    leaf('n6', 'result', 'b1', 'p/Gw.java:4:5'),
    box('b2', 'fetch', 'p.Gw#fetch(int)', 'b0', true),
    leaf('n7', 'x', 'b2', 'p/Gw.java:3:22', 'FUNC_PARAM'),
    { ...leaf('n8', 'call fetch', 'b2', 'p/Gw.java:3:5', 'EXTERNAL'), lead: 'call' },
    box('b3', 'save', 'p.Gw#save(int)', 'b0', true),
    leaf('n9', 'x', 'b3', 'p/Gw.java:8:21', 'FUNC_PARAM'),
    leaf('n10', 'orphan', undefined, 'p/App.java:2:1'),
    box('b4', 'helper', 'p.App#helper()', 'b0'),
    leaf('n11', 'h', 'b4', 'p/App.java:12:9'),
    box('b5', 'fetch', 'p.Gw#fetch(int)', 'b4', true),
    leaf('n12', 'x', 'b5', 'p/Gw.java:3:22', 'FUNC_PARAM'),
  ],
  edges: [
    edge('n1', 'n4'), edge('n4', 'n5'), edge('n5', 'n6'), edge('n6', 'n2'),
    edge('n1', 'n7'), edge('n7', 'n8'), edge('n5', 'n8', 'ORDER'),
    edge('n8', 'n2', 'TRUE'), edge('n2', 'n9'), edge('n6', 'n9', 'ORDER'), edge('n11', 'n12'),
  ],
  lines: {
    'p/App.java': { 5: 'int a = 1;', 6: 'int b = fetch(a);', 7: 'int c = 0;', 2: 'class App {', 12: 'int h = 2;' },
    'p/Gw.java': { 3: 'static int fetch(int x) {', 4: 'return 9;', 8: 'static void save(int x) {' },
  },
};

const sorted = (view: CondensedView) => ({
  nodes: [...view.nodes].sort((a, b) => a.id.localeCompare(b.id)),
  edges: view.edges.map((e) => `${e.source}->${e.target}:${e.kind}x${e.count}`).sort(),
});

// One group per boundary method however many call sites it has - three blocks of `fetch`, one nested
// in `helper` - and `program` for the rest, the box-less `n10` included.
test('the condensed view opens on one node per boundary method and one for the program', () => {
  assert.deepEqual(sorted(condensed(PAYLOAD, condensedOpening())).nodes, [
    { id: FETCH, label: 'fetch', type: 'GROUP', members: 6, boundary: true },
    { id: SAVE, label: 'save', type: 'GROUP', members: 1, boundary: true },
    { id: PROGRAM, label: 'program', type: 'GROUP', members: 5 },
  ].sort((a, b) => a.id.localeCompare(b.id)));
});

// The nearest box decides: `n12` is in `fetch`'s block inside `helper`, `n11` in `helper` itself.
test('a leaf belongs to the group of its nearest enclosing box', () => {
  const groups = groupOf(PAYLOAD);
  assert.equal(groups.get('n12'), FETCH);
  assert.equal(groups.get('n11'), PROGRAM);
  assert.equal(groups.get('n10'), PROGRAM);
  assert.equal(groups.size, 12);
});

// Inside `fetch`: n4->n5, n5->n6, n7->n8 and the ORDER n5->n8. None drawn; every crossing one is.
test('an edge between two members of one group is not drawn, and every crossing edge is', () => {
  assert.deepEqual(sorted(condensed(PAYLOAD, new Set())).edges, [
    `${FETCH}->${PROGRAM}:FLOWx1`,
    `${FETCH}->${PROGRAM}:TRUEx1`,
    `${FETCH}->${SAVE}:ORDERx1`,
    `${PROGRAM}->${FETCH}:FLOWx3`,
    `${PROGRAM}->${SAVE}:FLOWx1`,
  ].sort());
});

// fetch -> program is a FLOW (n6->n2) and a TRUE (n8->n2): two drawn edges, not one counted twice.
test('edges onto one pair of display nodes are one drawn edge per kind, with a count', () => {
  const edges = condensed(PAYLOAD, new Set()).edges.filter((e) => e.source === FETCH && e.target === PROGRAM);
  assert.deepEqual(edges.map((e) => [e.kind, e.count]).sort(), [['FLOW', 1], ['TRUE', 1]]);
  const into = condensed(PAYLOAD, new Set()).edges.filter((e) => e.source === PROGRAM && e.target === FETCH);
  assert.deepEqual(into.map((e) => [e.kind, e.count]), [['FLOW', 3]]);
});

// `b` has two edges in (n6, n8) and one out (n9); `n5`, two hops away, stays in its group.
test('clicking a leaf takes out its direct neighbours, in and out, and nothing further', () => {
  const after = tapCondensed(PAYLOAD, new Set(['n2']), { node: 'n2' });
  assert.deepEqual([...after].sort(), ['n2', 'n6', 'n8', 'n9']);
});

// From the opening: a, b and h have edges to the boundary groups; c and the orphan do not.
test('clicking a group takes out its frontier only', () => {
  const after = tapCondensed(PAYLOAD, new Set(), { node: PROGRAM });
  assert.deepEqual([...after].sort(), ['n1', 'n11', 'n2']);
});

// What is left of `program` after its frontier came out touches nothing, so a click takes it all.
test('clicking a group with no frontier takes out every member', () => {
  const after = tapCondensed(PAYLOAD, new Set(['n1', 'n2', 'n11']), { node: PROGRAM });
  assert.deepEqual([...after].sort(), ['n1', 'n10', 'n11', 'n2', 'n3']);
});

// The FLOW program->fetch stands for a->x twice and h->x; b's FLOW into save is another edge.
test('clicking a drawn edge takes out the endpoints of the edges it stands for, of its kind only', () => {
  const flow = tapCondensed(PAYLOAD, new Set(), { edge: [PROGRAM, FETCH, 'FLOW'] });
  assert.deepEqual([...flow].sort(), ['n1', 'n11', 'n12', 'n4', 'n7']);
  const branch = tapCondensed(PAYLOAD, new Set(), { edge: [FETCH, PROGRAM, 'TRUE'] });
  assert.deepEqual([...branch].sort(), ['n2', 'n8']);
});

// `save` has one member; once it is out the group is gone and its edges land on the leaf.
test('a group whose last member came out is gone, and its edges arrive at the member', () => {
  const view = sorted(condensed(PAYLOAD, new Set(['n9'])));
  assert.deepEqual(view.nodes.map((n) => n.id), [FETCH, 'n9', PROGRAM].sort((a, b) => a.localeCompare(b)));
  assert.deepEqual(view.nodes.find((n) => n.id === 'n9'), { id: 'n9', label: 'x', type: 'FUNC_PARAM' });
  assert.ok(view.edges.includes(`${FETCH}->n9:ORDERx1`));
  assert.ok(view.edges.includes(`${PROGRAM}->n9:FLOWx1`));
});

test('a revealed leaf keeps its lead', () => {
  const view = condensed(PAYLOAD, new Set(['n5']));
  assert.deepEqual(view.nodes.find((n) => n.id === 'n5'), { id: 'n5', label: 'call fetch', type: 'EXTERNAL', lead: 'call' });
});

test('reset returns to the opening view', () => {
  let revealed = condensedOpening();
  revealed = tapCondensed(PAYLOAD, revealed, { node: PROGRAM });
  revealed = tapCondensed(PAYLOAD, revealed, { node: 'n2' });
  assert.notDeepEqual(condensed(PAYLOAD, revealed), condensed(PAYLOAD, new Set()));
  assert.deepEqual(condensed(PAYLOAD, condensedOpening()), condensed(PAYLOAD, new Set()));
});

test('hovering a leaf shows its method and its line', () => {
  assert.deepEqual(hoverText(PAYLOAD, new Set(['n2']), { node: 'n2' }), ['App#main', 'App.java:6  int b = fetch(a);']);
  // A line the payload has no text for is still named.
  const bare = { ...PAYLOAD, lines: {} };
  assert.deepEqual(hoverText(bare, new Set(['n2']), { node: 'n2' }), ['App#main', 'App.java:6']);
});

// fetch's members are all in Gw.java's lines 3-4; program's come from main (2 is the orphan, which
// has no method) and helper.
test('hovering a group shows its member count and its members\' methods and line ranges', () => {
  assert.deepEqual(hoverText(PAYLOAD, new Set(), { node: FETCH }), ['fetch: 6 nodes', 'Gw#fetch  Gw.java:3-4']);
  assert.deepEqual(hoverText(PAYLOAD, new Set(['n1']), { node: PROGRAM }), [
    'program: 4 nodes', 'App#main  App.java:6-7', '(no method)  App.java:2', 'App#helper  App.java:12',
  ]);
});

// Three edges stand for program->fetch, all arriving on line 3 of Gw.java: one entry, not three.
test('hovering a drawn edge lists the distinct lines its values arrive on', () => {
  assert.deepEqual(hoverText(PAYLOAD, new Set(), { edge: [PROGRAM, FETCH, 'FLOW'] }),
    ['Gw#fetch  Gw.java:3  static int fetch(int x) {']);
  assert.deepEqual(hoverText(PAYLOAD, new Set(), { edge: [FETCH, PROGRAM, 'TRUE'] }),
    ['App#main  App.java:6  int b = fetch(a);']);
});

/** Twelve methods with one leaf each, every leaf fed from one source: twelve of everything. */
function wide(): Payload {
  const nodes: PayloadNode[] = [box('b0', 'main', 'p.App#main()'), leaf('s', 'source', 'b0', 'p/App.java:1:1')];
  const edges = [];
  for (let i = 1; i <= 12; i += 1) {
    nodes.push(box(`b${i}`, `m${i}`, `p.App#m${i}()`, 'b0'), leaf(`n${i}`, `v${i}`, `b${i}`, `p/App.java:${10 + i}:1`));
    edges.push(edge('s', `n${i}`));
  }
  return { nodes, edges };
}

test('a group\'s and an edge\'s hover list the first ten entries, then how many more', () => {
  const group = hoverText(wide(), new Set(), { node: PROGRAM });
  assert.equal(group.length, 12);
  assert.equal(group[0], 'program: 13 nodes');
  assert.equal(group[10], 'App#m9  App.java:19');
  assert.equal(group[11], '+3 more');

  const fan = hoverText(wide(), new Set(['s']), { edge: ['s', PROGRAM, 'FLOW'] });
  assert.equal(fan.length, 11);
  assert.equal(fan[9], 'App#m10  App.java:20');
  assert.equal(fan[10], '+2 more');
});
