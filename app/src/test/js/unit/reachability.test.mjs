import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCorpus } from './corpus.mjs';
import { opening, tap, screen } from '../../../main/resources/viewer/model.mjs';

const corpus = loadCorpus();

/**
 * Click everything on screen, repeatedly, until nothing new appears.
 *
 * Bounded by the payload: a click only ever adds, and there are finitely many leaves, so the set
 * grows or the loop stops. Box clicks are left out on purpose - a box can fold, so including them
 * would let the walk oscillate, and the claim being made is that the reader can get everywhere by
 * clicking what they can see.
 */
function fixpoint(payload) {
  let revealed = opening(payload);
  for (;;) {
    const before = screen(payload, revealed).showing;
    for (const id of before) revealed = tap(payload, revealed, id);
    const after = screen(payload, revealed).showing;
    if (after.size === before.size) return after;
  }
}

// The property d9c12ff swept 63 fixtures for by hand and nothing re-checked. `member` is what that
// cost: main calls app.func1() passing and returning nothing, so no edge crosses into the callee,
// and the node standing in for it did nothing when clicked.
test('every leaf can be put on screen by clicking what is on screen', () => {
  const short = [];
  for (const { name, payload } of corpus) {
    const leaves = payload.nodes.filter((n) => n.type !== 'METHOD');
    const reached = fixpoint(payload);
    if (reached.size !== leaves.length) short.push(`${name}: ${reached.size} of ${leaves.length}`);
  }
  assert.deepEqual(short, [], `fixtures the reader cannot fully reach:\n  ${short.join('\n  ')}`);
});
