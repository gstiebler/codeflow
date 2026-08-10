import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCorpus } from './corpus.ts';
import { opening, tap, screen, descendantLeaves, withStubs, openBoxes } from '../../../main/resources/viewer/model.ts';
import type { Payload } from '../../../main/resources/viewer/types.ts';

const corpus = loadCorpus();

/**
 * Two states per fixture: what the reader sees first, and what they see after clicking everything
 * on screen once. One state would let a property hold at open and break on the first click.
 */
function states(payload: Payload) {
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
      // `n.parent &&` is the root box: it is inside nothing, so no open box owes it anything.
      for (const box of payload.nodes.filter((n) => n.type === 'METHOD' && n.parent && open.has(n.parent))) {
        const inside = [...descendantLeaves(payload.nodes, box.id)].filter((leaf) => showing.has(leaf));
        assert.ok(inside.length > 0, `${name}: box ${box.label} is inside an open box with nothing showing in it`);
      }
    }
  }
});

// P3. The rule Cytoscape applies internally, swept over the corpus so that the renderer which
// derives nothing is told the same thing the one which derives everything works out.
//
// The corpus cannot tell descendants from own children, and that is a fact about the gestures rather
// than a gap here: a box is entered through its stub, a stub is the box's own RETURN, and a fold
// takes every descendant at once - so in both swept states a visible box has an own leaf showing,
// and the wrong implementation agrees with this one on all 64 fixtures. What separates them is the
// hand-written `nested` payload in screen.test.ts, where a box holds no leaf of its own at all.
// This sweep still catches a box drawn visible with nothing inside it, which is the other half.
test('a box is visible exactly when some descendant leaf is showing', () => {
  for (const { name, payload } of corpus) {
    for (const revealed of states(payload)) {
      const view = screen(payload, revealed);
      for (const node of view.nodes) {
        if (node.type !== 'METHOD') continue;
        const inside = [...descendantLeaves(payload.nodes, node.id)].some((leaf) => view.showing.has(leaf));
        assert.equal(node.visible, inside, `${name}: box ${node.label} is drawn ${node.visible} with ${inside} inside it`);
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
