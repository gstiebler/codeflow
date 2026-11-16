import { test } from 'node:test';
import assert from 'node:assert/strict';
import ELK from 'elkjs/lib/elk.bundled.js';
import {
  captionAt, elkGraph, flatElkGraph, positions, roundedPath, routes, type ElkNode, type Point,
} from '../../../main/resources/viewer/layout.ts';
import type { CondensedNode, CondensedView, View } from '../../../main/resources/viewer/types.ts';

const leaf = (id: string): CondensedNode => ({ id, label: id, type: 'VARIABLE' });
const flow = (source: string, target: string) => ({ source, target, kinds: { FLOW: 1 }, count: 1 });

// The condensed view asks ELK for orthogonal routes - a string, as every ELK option must be - and
// the boxed view is left as it was.
test('the condensed layout asks ELK for orthogonal routes, and the boxed one does not', () => {
  const flat = flatElkGraph({ nodes: [leaf('a')], edges: [] }, (n) => n.label);
  assert.equal(flat.layoutOptions!['elk.edgeRouting'], 'ORTHOGONAL');
  const boxed: View = { showing: new Set(), stubs: new Set(), hidden: new Map(), nodes: [], edges: [] };
  assert.equal(elkGraph(boxed).layoutOptions!['elk.edgeRouting'], undefined);
});

// A laid-out result as ELK returns it: `a` sits above `b`, and `b -> a` goes back up around the side,
// so neither route is the straight line between the two nodes.
const laidOut: ElkNode = {
  id: 'root',
  children: [
    { id: 'a', x: 12, y: 12, width: 60, height: 26 },
    { id: 'b', x: 12, y: 108, width: 60, height: 26 },
  ],
  edges: [
    {
      id: 'a->b', sources: ['a'], targets: ['b'],
      sections: [{ id: 'a->b_s0', startPoint: { x: 32, y: 38 }, endPoint: { x: 32, y: 108 } }],
    },
    {
      id: 'b->a', sources: ['b'], targets: ['a'],
      sections: [{
        id: 'b->a_s0', startPoint: { x: 52, y: 108 }, endPoint: { x: 52, y: 38 },
        bendPoints: [{ x: 52, y: 90 }, { x: 90, y: 90 }, { x: 90, y: 60 }, { x: 52, y: 60 }],
      }],
    },
  ],
};

test('a route is the section ELK computed: start, every bend in order, end', () => {
  const read = routes(laidOut);
  assert.deepEqual(read.get('a->b'), [{ x: 32, y: 38 }, { x: 32, y: 108 }]);
  assert.deepEqual(read.get('b->a'), [
    { x: 52, y: 108 }, { x: 52, y: 90 }, { x: 90, y: 90 }, { x: 90, y: 60 }, { x: 52, y: 60 }, { x: 52, y: 38 },
  ]);
  assert.deepEqual([...read.keys()], ['a->b', 'b->a']);
});

// A straight line would be a guess dressed as ELK's answer - exactly a line through a node.
test('an edge ELK gave no section is an error, never a straight line', () => {
  const without = (sections: ElkNode['edges']) => ({ ...laidOut, edges: sections });
  assert.throws(() => routes(without([{ id: 'a->b', sources: ['a'], targets: ['b'] }])), /a->b/);
  assert.throws(() => routes(without([{ id: 'a->b', sources: ['a'], targets: ['b'], sections: [] }])), /a->b/);
  const two = laidOut.edges![0].sections![0];
  assert.throws(() => routes(without([{ id: 'a->b', sources: ['a'], targets: ['b'], sections: [two, two] }])), /a->b/);
  // The pair: the same edge with its one section reads.
  assert.equal(routes(without([laidOut.edges![0]])).get('a->b')!.length, 2);
});

const onBorder = (p: Point, box: { x: number; y: number; width: number; height: number }) =>
  p.x >= box.x - 0.5 && p.x <= box.x + box.width + 0.5 && p.y >= box.y - 0.5 && p.y <= box.y + box.height + 0.5
  && (Math.abs(p.y - box.y) < 0.5 || Math.abs(p.y - box.y - box.height) < 0.5
    || Math.abs(p.x - box.x) < 0.5 || Math.abs(p.x - box.x - box.width) < 0.5);

// A flat graph's routes come back in the frame its nodes do, so nothing is re-based: every route
// starts on its source's border and ends on its target's, including one that goes back up.
test('ELK routes a flat graph in the frame of its nodes', async () => {
  const view: CondensedView = {
    nodes: ['a', 'b', 'c'].map(leaf),
    edges: [flow('a', 'b'), flow('a', 'c'), flow('c', 'a'), flow('b', 'c')],
  };
  const result = await new ELK().layout(flatElkGraph(view, (n) => n.label)) as ElkNode;
  const at = new Map(positions(result).map((p) => [p.id, p]));
  const read = routes(result);
  assert.equal(read.size, view.edges.length);
  for (const edge of view.edges) {
    const route = read.get(`${edge.source}->${edge.target}`)!;
    assert.ok(onBorder(route[0], at.get(edge.source)!), `${edge.source}->${edge.target} starts off its source`);
    assert.ok(onBorder(route[route.length - 1], at.get(edge.target)!), `${edge.source}->${edge.target} ends off its target`);
    // Orthogonal: every segment is horizontal or vertical.
    for (let i = 1; i < route.length; i++) {
      assert.ok(route[i].x === route[i - 1].x || route[i].y === route[i - 1].y);
    }
  }
});

test('the caption sits at the midpoint of the longest segment', () => {
  assert.deepEqual(captionAt([{ x: 0, y: 0 }, { x: 0, y: 10 }, { x: 60, y: 10 }, { x: 60, y: 30 }]), { x: 30, y: 10 });
  assert.deepEqual(captionAt([{ x: 0, y: 0 }, { x: 0, y: 10 }, { x: 40, y: 10 }, { x: 40, y: 100 }]), { x: 40, y: 55 });
  assert.deepEqual(captionAt([{ x: 0, y: 0 }, { x: 0, y: 30 }]), { x: 0, y: 15 });
  // The pair: not the midpoint of the whole route, nor of its first segment.
  assert.notDeepEqual(captionAt([{ x: 0, y: 0 }, { x: 0, y: 4 }, { x: 100, y: 4 }]), { x: 0, y: 2 });
});

test('a rounded path runs from the first point to the last, one curve per bend', () => {
  assert.equal(roundedPath([{ x: 0, y: 0 }, { x: 0, y: 50 }], 6), 'M 0 0 L 0 50');
  const bent = roundedPath([{ x: 0, y: 0 }, { x: 0, y: 50 }, { x: 40, y: 50 }], 6);
  assert.equal(bent, 'M 0 0 L 0 44 Q 0 50 6 50 L 40 50');
  // A corner never reaches past the middle of a short segment.
  assert.equal(roundedPath([{ x: 0, y: 0 }, { x: 0, y: 4 }, { x: 40, y: 4 }], 6), 'M 0 0 L 0 2 Q 0 4 2 4 L 40 4');
  // A repeated point is passed through, not divided by.
  assert.equal(roundedPath([{ x: 0, y: 0 }, { x: 0, y: 0 }, { x: 0, y: 9 }], 6), 'M 0 0 L 0 0 L 0 9');
});
