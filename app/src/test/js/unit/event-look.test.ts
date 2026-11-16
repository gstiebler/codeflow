import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { PALETTE, edgeStyle } from '../../../main/resources/viewer/theme.ts';

// An entry point's event is filled in its edges' teal, the fill the Mermaid document gives it - read
// from the Kotlin as text, since the two definitions live in two languages.
test('an EVENT node is filled with the classDef MermaidExporter gives it', () => {
  const kotlin = readFileSync('app/src/main/kotlin/codeflow/MermaidExporter.kt', 'utf8');
  const hex = kotlin.match(/classDef EVENT fill:#([0-9A-Fa-f]{8})/);
  assert.ok(hex, 'MermaidExporter has no EVENT classDef');
  const [r, g, b, a] = [0, 2, 4, 6].map((i) => parseInt(hex[1].slice(i, i + 2), 16));
  assert.equal(PALETTE.EVENT, `rgba(${r}, ${g}, ${b}, ${Math.round((a / 255) * 100) / 100})`);
  // The pair: the event is not drawn as the boundary token, the other node that is not a value.
  assert.notEqual(PALETTE.EVENT, PALETTE.BOUNDARIES);
});

// An event edge says "set going", not "flowed into", and must not read as flow.
test('an EVENT edge is drawn in teal, dashed and captioned', () => {
  assert.deepEqual(edgeStyle('EVENT'), { colour: '#00897b', label: 'event', dashed: true });
  // The pair: the other dashed edge that is not a value keeps its own colour.
  assert.notEqual(edgeStyle('ORDER').colour, edgeStyle('EVENT').colour);
});
