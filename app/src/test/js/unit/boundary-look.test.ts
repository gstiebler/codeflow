import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PALETTE } from '../../../main/resources/viewer/theme.ts';
import { captionParts } from '../../../main/resources/viewer/condensed.ts';

// The boundary-order token's boxes are filled in the ORDER edges' purple, the same fill the Mermaid
// document gives them - read from the Kotlin as text, since the two definitions live in two languages.
test('a BOUNDARIES node is filled with the classDef MermaidExporter gives it', () => {
  const kotlin = readFileSync('app/src/main/kotlin/codeflow/MermaidExporter.kt', 'utf8');
  const hex = kotlin.match(/classDef BOUNDARIES fill:#([0-9A-Fa-f]{8})/);
  assert.ok(hex, 'MermaidExporter has no BOUNDARIES classDef');
  const [r, g, b, a] = [0, 2, 4, 6].map((i) => parseInt(hex[1].slice(i, i + 2), 16));
  assert.equal(PALETTE.BOUNDARIES, `rgba(${r}, ${g}, ${b}, ${Math.round((a / 255) * 100) / 100})`);
  // The pair: the token is not drawn as the variable it used to be.
  assert.notEqual(PALETTE.BOUNDARIES, PALETTE.VARIABLE);
});

// Only the payload's `lead` is bold - never a prefix found in the label. The counts stay in the rest.
test('a node with a lead is cut into the lead and the rest of its badge', () => {
  assert.deepEqual(captionParts({ badge: 'call fetch ↓2', lead: 'call' }), { lead: 'call', rest: ' fetch ↓2' });
  // The pair: an outside call named `call`, with no lead, stays whole and plain.
  assert.deepEqual(captionParts({ badge: 'call' }), { rest: 'call' });
  assert.deepEqual(captionParts({ badge: 'callback ↑1' }), { rest: 'callback ↑1' });
});

// A lead the badge does not start with is a payload this viewer was not written for: drawn whole.
test('a lead the badge does not start with is not drawn bold', () => {
  assert.deepEqual(captionParts({ badge: 'fetch', lead: 'call' }), { rest: 'fetch' });
});
