import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  condensed, condensedCaption, condensedOpening, groupOf, hoverText, tapCondensed,
} from '../../../main/resources/viewer/condensed.ts';
import type { CondensedView, EdgeKind, Payload, PayloadNode } from '../../../main/resources/viewer/types.ts';
import { loadCorpus } from './corpus.ts';

const box = (id: string, label: string, method: string, parent?: string, boundary = false, called?: string): PayloadNode =>
  ({ id, label, type: 'METHOD', method, ...(parent ? { parent } : {}), ...(boundary ? { boundary } : {}), ...(called ? { called } : {}) });
const leaf = (id: string, label: string, parent: string | undefined, source: string, type: PayloadNode['type'] = 'VARIABLE'): PayloadNode =>
  ({ id, label, type, source, ...(parent ? { parent } : {}) });
const edge = (source: string, target: string, kind: EdgeKind = 'FLOW') => ({ source, target, kind });

/**
 * `main` (`b0`) calls the boundary `fetch` twice (`b1`, `b2`) and `save` once (`b3`), and `helper`
 * (`b4`), which calls `fetch` again (`b5`) - so `fetch` has three boxes at three call sites, one nested
 * in a non-boundary box. `c` and `n10` touch nothing.
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
    leaf('n10', 'orphan', 'b0', 'p/App.java:2:1'),
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

/** `FLOW1+TRUE1x2`: the kinds a drawn edge stands for, each with its count, then the total. */
const kindsOf = (e: CondensedView['edges'][number]) =>
  `${Object.entries(e.kinds).sort().map(([k, n]) => `${k}${n}`).join('+')}x${e.count}`;

const sorted = (view: CondensedView) => ({
  nodes: [...view.nodes].sort((a, b) => a.id.localeCompare(b.id)),
  edges: view.edges.map((e) => `${e.source}->${e.target}:${kindsOf(e)}`).sort(),
});

// One group per call site: `fetch` is three groups (b1, b2, b5), `main` and `helper` one each.
test('the condensed view opens on one node per call-site box', () => {
  assert.deepEqual(sorted(condensed(PAYLOAD, condensedOpening(PAYLOAD))).nodes, [
    { id: 'b0', label: 'main', type: 'GROUP', members: 4 },
    { id: 'b1', label: 'fetch', type: 'GROUP', members: 3, boundary: true },
    { id: 'b2', label: 'fetch', type: 'GROUP', members: 2, boundary: true },
    { id: 'b3', label: 'save', type: 'GROUP', members: 1, boundary: true },
    { id: 'b4', label: 'helper', type: 'GROUP', members: 1 },
    { id: 'b5', label: 'fetch', type: 'GROUP', members: 1, boundary: true },
  ]);
});

// The box a leaf sits directly in decides: `n12` is in `fetch`'s box inside `helper`, `n11` in `helper`.
test('a leaf belongs to the group of the box it sits in', () => {
  const groups = groupOf(PAYLOAD);
  assert.equal(groups.get('n12'), 'b5');
  assert.equal(groups.get('n11'), 'b4');
  assert.equal(groups.get('n10'), 'b0');
  assert.equal(groups.size, 12);
});

test('a method called twice is two groups', () => {
  const p: Payload = {
    nodes: [box('b1', 'f', 'p.A#f()'), leaf('x', 'x', 'b1', 'p/A.java:1:1'), box('b2', 'f', 'p.A#f()'), leaf('y', 'y', 'b2', 'p/A.java:1:1')],
    edges: [],
  };
  const groups = condensed(p, new Set()).nodes.filter((n) => n.type === 'GROUP');
  assert.deepEqual(groups.map((n) => [n.id, n.label]), [['b1', 'f'], ['b2', 'f']]);
});

test('a box with no leaves of its own is not drawn', () => {
  const p: Payload = {
    nodes: [box('outer', 'o', 'p.A#o()'), box('inner', 'i', 'p.A#i()', 'outer'), leaf('x', 'x', 'inner', 'p/A.java:1:1')],
    edges: [],
  };
  assert.deepEqual(condensed(p, new Set()).nodes.map((n) => n.id), ['inner']);
});

test('a leaf without a box throws', () => {
  const p: Payload = { nodes: [leaf('x', 'x', undefined, 'p/A.java:1:1')], edges: [] };
  assert.throws(() => condensed(p, new Set()), /has no method box/);
});

test('a boundary box keeps the boundary look', () => {
  const nodes = condensed(PAYLOAD, new Set()).nodes;
  assert.equal(nodes.find((n) => n.id === 'b1')!.boundary, true);
  assert.ok(!('boundary' in nodes.find((n) => n.id === 'b0')!));
});

// Inside `fetch` (b1): n4->n5, n5->n6; inside b2: n7->n8. None drawn; every crossing one is.
test('an edge between two members of one group is not drawn, and every crossing edge is', () => {
  assert.deepEqual(sorted(condensed(PAYLOAD, new Set())).edges, [
    'b0->b1:FLOW1x1', 'b1->b0:FLOW1x1', 'b0->b2:FLOW1x1', 'b1->b2:ORDER1x1',
    'b2->b0:TRUE1x1', 'b0->b3:FLOW1x1', 'b1->b3:ORDER1x1', 'b4->b5:FLOW1x1',
  ].sort());
});

// Edges onto one ordered pair are one drawn edge, whatever their kinds, with each kind's count kept
// for the hover: here b0 -> b1 holds a FLOW and an ORDER.
test('edges onto one ordered pair of display nodes are one drawn edge, whatever their kinds', () => {
  const p: Payload = {
    nodes: [box('a', 'a', 'p.A#a()'), box('b', 'b', 'p.A#b()'), leaf('x', 'x', 'a', 'p/A.java:1:1'), leaf('y', 'y', 'b', 'p/A.java:2:1')],
    edges: [edge('x', 'y'), edge('x', 'y', 'ORDER'), edge('y', 'x')],
  };
  const view = condensed(p, new Set());
  assert.deepEqual(view.edges.filter((e) => e.source === 'a').map((e) => [e.kinds, e.count]), [[{ FLOW: 1, ORDER: 1 }, 2]]);
  assert.deepEqual(view.edges.filter((e) => e.source === 'b').map((e) => [e.kinds, e.count]), [[{ FLOW: 1 }, 1]]);
});

/** Every ordered pair drawn more than once - what the opening must not have. */
const repeated = (view: CondensedView) => {
  const pairs = view.edges.map((e) => `${e.source}->${e.target}`);
  return pairs.filter((p, i) => pairs.indexOf(p) !== i);
};

// `boundary` is the page that read as a tangle: its event and its three boundary groups trade flow,
// order and event edges, one drawn edge per kind before. Now each ordered pair is drawn once, and
// together they still stand for every payload edge that crosses between two display nodes.
test('no two drawn edges share an ordered pair, on the hand-written payload and on boundary\'s opening', () => {
  assert.deepEqual(repeated(condensed(PAYLOAD, new Set())), []);
  const { payload } = loadCorpus().find((c) => c.name === 'boundary')!;
  const opening = condensed(payload, condensedOpening(payload));
  assert.deepEqual(repeated(opening), []);
  // The pair: merging lost nothing - the counts add up to the crossing edges, kind by kind.
  const groups = groupOf(payload);
  const shown = (id: string) => (condensedOpening(payload).has(id) ? id : groups.get(id)!);
  const crossing = payload.edges.filter((e) => shown(e.source) !== shown(e.target));
  assert.ok(opening.edges.some((e) => Object.keys(e.kinds).length > 1), 'boundary\'s opening has no mixed pair');
  assert.equal(opening.edges.reduce((sum, e) => sum + e.count, 0), crossing.length);
  for (const e of opening.edges) {
    assert.equal(Object.values(e.kinds).reduce((a, b) => a + b, 0), e.count, `${e.source}->${e.target}`);
  }
});

// `b` has two edges in (n6, n8) and one out (n9); `n5`, two hops away, stays in its group.
test('clicking a leaf takes out its direct neighbours, in and out, and nothing further', () => {
  const after = tapCondensed(PAYLOAD, new Set(['n2']), { node: 'n2' });
  assert.deepEqual([...after].sort(), ['n2', 'n6', 'n8', 'n9']);
});

// From the opening: a and b have edges to the other boxes; c and the orphan do not.
test('clicking a group takes out its frontier only', () => {
  const after = tapCondensed(PAYLOAD, new Set(), { node: 'b0' });
  assert.deepEqual([...after].sort(), ['n1', 'n2']);
});

// What is left of `main` after its frontier came out touches nothing, so a click takes it all.
test('clicking a group with no frontier takes out every member', () => {
  const after = tapCondensed(PAYLOAD, new Set(['n1', 'n2']), { node: 'b0' });
  assert.deepEqual([...after].sort(), ['n1', 'n10', 'n2', 'n3']);
});

test('clicking a drawn edge takes out the endpoints of the edges it stands for', () => {
  assert.deepEqual([...tapCondensed(PAYLOAD, new Set(), { edge: ['b0', 'b1'] })].sort(), ['n1', 'n4']);
  assert.deepEqual([...tapCondensed(PAYLOAD, new Set(), { edge: ['b1', 'b0'] })].sort(), ['n2', 'n6']);
});

// `save` has one member; once it is out the group is gone and its edges land on the leaf.
test('a group whose last member came out is gone, and its edges arrive at the member', () => {
  const view = sorted(condensed(PAYLOAD, new Set(['n9'])));
  assert.deepEqual(view.nodes.map((n) => n.id), ['b0', 'b1', 'b2', 'b4', 'b5', 'n9']);
  assert.deepEqual(view.nodes.find((n) => n.id === 'n9'), { id: 'n9', label: 'x', type: 'FUNC_PARAM' });
  assert.ok(view.edges.includes('b1->n9:ORDER1x1'));
  assert.ok(view.edges.includes('b0->n9:FLOW1x1'));
});

test('a revealed leaf keeps its lead', () => {
  const view = condensed(PAYLOAD, new Set(['n5']));
  assert.deepEqual(view.nodes.find((n) => n.id === 'n5'), { id: 'n5', label: 'call fetch', type: 'EXTERNAL', lead: 'call' });
});

test('reset returns to the opening view', () => {
  let revealed = condensedOpening(PAYLOAD);
  revealed = tapCondensed(PAYLOAD, revealed, { node: 'b0' });
  revealed = tapCondensed(PAYLOAD, revealed, { node: 'n2' });
  assert.notDeepEqual(condensed(PAYLOAD, revealed), condensed(PAYLOAD, new Set()));
  assert.deepEqual(condensed(PAYLOAD, condensedOpening(PAYLOAD)), condensed(PAYLOAD, new Set()));
});

// Two roots, two events: each event is taken out of its group from the start, beside the call-site
// groups - and Reset, which asks for the opening again, comes back to the same page.
test('the condensed view opens with every entry point\'s event on its own, and Reset returns to it', () => {
  const events: Payload = {
    ...PAYLOAD,
    nodes: [
      ...PAYLOAD.nodes,
      leaf('e1', 'event main', 'b0', 'p/App.java:4:5', 'EVENT'),
      box('b6', 'other', 'p.App#other()'),
      leaf('e2', 'event other', 'b6', 'p/App.java:20:5', 'EVENT'),
      leaf('n13', 'o', 'b6', 'p/App.java:21:9'),
    ],
    edges: [...PAYLOAD.edges, edge('e1', 'n1', 'EVENT'), edge('e1', 'n3', 'EVENT'), edge('e2', 'n13', 'EVENT')],
  };
  const opening = condensedOpening(events);
  assert.deepEqual([...opening].sort(), ['e1', 'e2']);
  const view = sorted(condensed(events, opening));
  assert.deepEqual(view.nodes.map((n) => `${n.id}:${n.type}`), ['b0', 'b1', 'b2', 'b3', 'b4', 'b5', 'b6', 'e1', 'e2'].map((id) =>
    `${id}:${id.startsWith('e') ? 'EVENT' : 'GROUP'}`).sort());
  // The pair: the event's targets stay in their group, which the event points into.
  assert.ok(!view.nodes.some((n) => n.id === 'n1' || n.id === 'n13'));
  assert.ok(view.edges.includes('e1->b0:EVENT2x2'), view.edges.join(' '));

  let revealed = tapCondensed(events, opening, { node: 'b0' });
  assert.notDeepEqual(condensed(events, revealed), condensed(events, opening));
  revealed = condensedOpening(events);
  assert.deepEqual(condensed(events, revealed), condensed(events, opening));
});

test('hovering a leaf shows its method and its line', () => {
  assert.deepEqual(hoverText(PAYLOAD, new Set(['n2']), { node: 'n2' }), ['App#main  App.java:6  int b = fetch(a);']);
  // A line the payload has no text for is still named.
  const bare = { ...PAYLOAD, lines: {} };
  assert.deepEqual(hoverText(bare, new Set(['n2']), { node: 'n2' }), ['App#main  App.java:6']);
  assert.deepEqual(hoverText(PAYLOAD, new Set(['n10']), { node: 'n10' }), ['App#main  App.java:2  class App {']);
});

// n12 is in fetch, called by helper on line 13, called by main on line 7: three frames, innermost
// first, each naming the line running in that method - the stack a Java trace would print.
test('hovering a leaf deep in calls shows its call stack', () => {
  const called: Record<string, string> = { b4: 'p/App.java:7:9', b5: 'p/App.java:13:9' };
  const traced: Payload = {
    ...PAYLOAD,
    nodes: PAYLOAD.nodes.map((n) => (called[n.id] ? { ...n, called: called[n.id] } : n)),
    lines: { ...PAYLOAD.lines, 'p/App.java': { ...PAYLOAD.lines!['p/App.java'], 13: 'fetch(h);' } },
  };
  assert.deepEqual(hoverText(traced, new Set(['n12']), { node: 'n12' }), [
    'Gw#fetch  Gw.java:3  static int fetch(int x) {',
    'App#helper  App.java:13  fetch(h);',
    'App#main  App.java:7  int c = 0;',
  ]);
  // A payload whose boxes say nothing of their callers stops at the first box.
  assert.deepEqual(hoverText(PAYLOAD, new Set(['n12']), { node: 'n12' }), ['Gw#fetch  Gw.java:3  static int fetch(int x) {']);
});

// b1's members are in Gw.java's lines 3-4; main's remaining ones come from lines 2, 6 and 7.
test('hovering a group shows its member count and its members\' methods and line ranges', () => {
  assert.deepEqual(hoverText(PAYLOAD, new Set(), { node: 'b1' }), ['fetch: 3 nodes', 'Gw#fetch  Gw.java:3-4']);
  assert.deepEqual(hoverText(PAYLOAD, new Set(['n1']), { node: 'b0' }), ['main: 3 nodes', 'App#main  App.java:2-7']);
});

test('the group hover names the call site', () => {
  const p: Payload = {
    nodes: [box('b1', 'f', 'p.A#f()', undefined, false, 'p/A.java:12:5'), leaf('x', 'x', 'b1', 'p/A.java:13:1')],
    edges: [],
  };
  assert.deepEqual(hoverText(p, new Set(), { node: 'b1' }), ['f: 1 nodes', 'called at A.java:12', 'A#f  A.java:13']);
  // A root has no `called`, so no line.
  const root: Payload = { nodes: [box('r', 'f', 'p.A#f()'), leaf('x', 'x', 'r', 'p/A.java:13:1')], edges: [] };
  assert.deepEqual(hoverText(root, new Set(), { node: 'r' }), ['f: 1 nodes', 'A#f  A.java:13']);
});

// b0 -> b1 is the one edge n1 -> n4, arriving on line 3 of Gw.java.
test('hovering a drawn edge lists the distinct lines its values arrive on, after its kinds', () => {
  assert.deepEqual(hoverText(PAYLOAD, new Set(), { edge: ['b0', 'b1'] }),
    ['flow ×1', 'Gw#fetch  Gw.java:3  static int fetch(int x) {']);
  assert.deepEqual(hoverText(PAYLOAD, new Set(), { edge: ['b1', 'b0'] }),
    ['flow ×1', 'App#main  App.java:6  int b = fetch(a);']);
});

// The line carries no kind words any more, so the hover says them - in EdgeKind's order, not the
// order the payload happens to list the edges in: here the ORDER edge comes first.
test('hovering a merged edge starts with its kinds and their counts, in a fixed order', () => {
  const mixed: Payload = {
    nodes: [box('bx', 'm', 'p.App#m()'), leaf('a', 'a', 'bx', 'p/App.java:1:1'), leaf('b', 'b', 'bx', 'p/App.java:2:1'),
      leaf('c', 'c', 'bx', 'p/App.java:3:1')],
    edges: [edge('a', 'c', 'ORDER'), edge('b', 'c', 'EVENT'), edge('a', 'c'), edge('b', 'c', 'TRUE'), edge('a', 'c')],
  };
  const revealed = new Set(['c']);
  assert.deepEqual(hoverText(mixed, revealed, { edge: ['bx', 'c'] }), ['flow ×2, true ×1, order ×1, event ×1', 'App#m  App.java:3']);
});

/** Thirteen leaves in one box, the twelve of them in a file of their own. */
function wideBox(): Payload {
  const nodes: PayloadNode[] = [box('b0', 'main', 'p.App#main()'), leaf('s', 'source', 'b0', 'p/App.java:1:1')];
  for (let i = 1; i <= 12; i += 1) nodes.push(leaf(`n${i}`, `v${i}`, 'b0', `p/F${i}.java:1:1`));
  return { nodes, edges: [] };
}

/** One source feeding twelve leaves of another box, each on a line of its own. */
function fan(): Payload {
  const nodes: PayloadNode[] = [box('b0', 'main', 'p.App#main()'), leaf('s', 'source', 'b0', 'p/App.java:1:1'), box('b1', 'm', 'p.App#m()')];
  const edges = [];
  for (let i = 1; i <= 12; i += 1) {
    nodes.push(leaf(`n${i}`, `v${i}`, 'b1', `p/App.java:${10 + i}:1`));
    edges.push(edge('s', `n${i}`));
  }
  return { nodes, edges };
}

test('a group\'s and an edge\'s hover list the first ten entries, then how many more', () => {
  const group = hoverText(wideBox(), new Set(), { node: 'b0' });
  assert.equal(group.length, 12);
  assert.equal(group[0], 'main: 13 nodes');
  assert.equal(group[10], 'App#main  F9.java:1');
  assert.equal(group[11], '+3 more');

  const out = hoverText(fan(), new Set(['s']), { edge: ['s', 'b1'] });
  assert.equal(out.length, 12);
  assert.equal(out[0], 'flow ×12');
  assert.equal(out[10], 'App#m  App.java:20');
  assert.equal(out[11], '+2 more');
});

// A group says how many it stands for, so a click that takes members out visibly shrinks it; a leaf
// is its label, with nothing added.
test('a group is captioned with its member count, a leaf with its label', () => {
  assert.equal(condensedCaption({ id: 'b0', label: 'fetch', type: 'GROUP', members: 3 }), 'fetch (3)');
  assert.equal(condensedCaption({ id: 'n1', label: 'total', type: 'VARIABLE' }), 'total');
});
