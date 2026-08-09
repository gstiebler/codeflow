import { test } from 'node:test';
import assert from 'node:assert/strict';
import { withStubs, ownLeaves } from '../../../main/resources/viewer/model.mjs';

// main { main, x, f { f, a, g { g, b } } } - two levels of nesting, so "one level of lookahead"
// is a claim the fixture can actually falsify.
const nodes = [
  { id: 'm', type: 'METHOD', label: 'main' },
  { id: 'mR', type: 'RETURN', label: 'main', parent: 'm' },
  { id: 'x', type: 'VARIABLE', label: 'x', parent: 'm' },
  { id: 'f', type: 'METHOD', label: 'f', parent: 'm' },
  { id: 'fR', type: 'RETURN', label: 'f', parent: 'f' },
  { id: 'a', type: 'VARIABLE', label: 'a', parent: 'f' },
  { id: 'g', type: 'METHOD', label: 'g', parent: 'f' },
  { id: 'gR', type: 'RETURN', label: 'g', parent: 'g' },
  { id: 'b', type: 'VARIABLE', label: 'b', parent: 'g' },
];

const showing = (revealed) => [...withStubs(nodes, new Set(revealed))].sort();

// The whole point: a callee is drawn because its caller is open, not because a value flowed into
// it. `member` has no edge at all from main into func1, so nothing else would ever draw it.
test('a box inside an open box is offered as its RETURN node', () => {
  assert.deepEqual(showing(['mR', 'x']), ['fR', 'mR', 'x']);
});

// Cytoscape will not draw a compound parent with no visible children whatever its own display says,
// so the stub is the only thing that makes a closed box exist on the page at all.
test('the stub is a real leaf, so the box it stands for has something to draw', () => {
  assert.equal(withStubs(nodes, new Set(['mR', 'x'])).has('fR'), true);
});

// Without this the whole call tree unfolds at open, which is the wall the viewer exists to avoid.
test('a box inside a closed box is not offered', () => {
  assert.equal(showing(['mR', 'x']).includes('gR'), false);
});

// The trap in the derivation: if a stub counted as its box being open, offering f's stub would
// offer g's, then g's would offer the next, and one pass would unfold every callee in the payload.
test('a stub alone does not open the box it stands for', () => {
  assert.deepEqual(showing(['mR', 'x', 'fR']), ['fR', 'mR', 'x']);
});

// And the box that is now open drops its own stub: what is on screen is what the reader asked for,
// not the method's name kept around as a caption.
test('opening a box offers the boxes inside it', () => {
  assert.deepEqual(showing(['mR', 'x', 'a']), ['a', 'gR', 'mR', 'x']);
});

// The same case the "never hide a METHOD node" rule exists for: f has none of its own leaves
// revealed and is still open, on the strength of a grandchild. Read `fR` being absent as the
// assertion - treating f as closed would offer its stub, since its caller is open.
test('a box open only through a grandchild counts as open', () => {
  assert.deepEqual(showing(['mR', 'x', 'b']), ['b', 'mR', 'x']);
});

test('nothing revealed still offers the root box its own stub is inside', () => {
  assert.deepEqual(showing([]), []);
});

// What a box click expands to. Direct children only: a box holds boxes, and taking the descendants
// would open the whole subtree at one click.
test('a box expands to its own leaves and not to a nested box\'s', () => {
  assert.deepEqual([...ownLeaves(nodes, 'f')].sort(), ['a', 'fR']);
});

test('the root box expands to its own leaves only', () => {
  assert.deepEqual([...ownLeaves(nodes, 'm')].sort(), ['mR', 'x']);
});
