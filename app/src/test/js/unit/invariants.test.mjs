import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCorpus } from './corpus.mjs';
import { opening, tap, screen, descendantLeaves, withStubs, openBoxes } from '../../../main/resources/viewer/model.mjs';

const corpus = loadCorpus();

/**
 * Two states per fixture: what the reader sees first, and what they see after clicking everything
 * on screen once. One state would let a property hold at open and break on the first click.
 */
function states(payload) {
  const open = opening(payload);
  let clicked = new Set(open);
  for (const id of withStubs(payload.nodes, open)) clicked = tap(payload, clicked, id);
  return [open, clicked];
}

// P2. Cytoscape will not draw a compound node with no visible children, whatever display it is
// given, so a box offered to the reader with nothing showing inside it is a box that is not there.
test('every box whose parent is open has something showing inside it', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      const { showing } = screen(payload, revealed);
      const open = openBoxes(payload.nodes, revealed);
      for (const box of payload.nodes.filter((n) => n.type === 'METHOD' && open.has(n.parent))) {
        const inside = [...descendantLeaves(payload.nodes, box.id)].filter((leaf) => showing.has(leaf));
        assert.ok(inside.length > 0, `${name}: box ${box.label} is inside an open box with nothing showing in it`);
      }
    }
  }
});

// P3. The rule that keeps a box whose only visible node is a grandchild from being hidden, and that
// grandchild from having nowhere to live.
test('no METHOD node is ever given a display', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      for (const node of screen(payload, revealed).nodes) {
        if (node.type === 'METHOD') {
          assert.equal(node.display, null, `${name}: box ${node.label} was given display ${node.display}`);
        }
      }
    }
  }
});

// P4. The per-node counts and the edge list, from opposite directions: badgeLabel reads the former
// and the reader believes it about the latter.
test('the hidden counts add up to the edges that cross the screen edge', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      const { showing, hidden } = screen(payload, revealed);
      let counted = 0;
      for (const { in: incoming, out } of hidden.values()) counted += incoming + out;
      const crossing = payload.edges.filter(
        (e) => showing.has(e.source) !== showing.has(e.target),
      ).length;
      assert.equal(counted, crossing, `${name}: badges claim ${counted} hidden edges, the payload has ${crossing}`);
    }
  }
});
