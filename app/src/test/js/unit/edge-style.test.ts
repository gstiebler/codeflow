import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { EDGE_COLOURS, EDGE_DEFAULT, edgeStyle } from '../../../main/resources/viewer/theme.ts';

// The boundary-order token's edges say "ran before", not "flowed into", and must not read as flow.
test('an ORDER edge is drawn in the ORDER colour, dashed and captioned', () => {
  assert.deepEqual(edgeStyle('ORDER'), { colour: '#7b1fa2', label: 'order', dashed: true });
  // The positive's pair: a plain edge keeps the default grey, no caption, solid.
  assert.deepEqual(edgeStyle('FLOW'), { colour: EDGE_DEFAULT, label: undefined, dashed: false });
});

// One graph must not change colour between the Mermaid document and the page, and the two
// definitions live in two languages - so the Kotlin one is read as text and held to this one.
test('every edge colour is the stroke MermaidExporter draws that kind in', () => {
  const kotlin = readFileSync('app/src/main/kotlin/codeflow/MermaidExporter.kt', 'utf8');
  const strokes = Object.fromEntries(
    [...kotlin.matchAll(/EdgeKind\.(\w+) to "(#[0-9a-f]{6})"/g)].map((m) => [m[1], m[2]]),
  );
  assert.ok('ORDER' in strokes, `MermaidExporter has no ORDER stroke: ${JSON.stringify(strokes)}`);
  assert.deepEqual(strokes, EDGE_COLOURS);
});
