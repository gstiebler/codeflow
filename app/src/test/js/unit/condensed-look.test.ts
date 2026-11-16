import { test } from 'node:test';
import assert from 'node:assert/strict';
import { condensed, condensedOpening } from '../../../main/resources/viewer/condensed.ts';
import { EDGE_DEFAULT, condensedEdgeStyle, edgeStyle } from '../../../main/resources/viewer/theme.ts';
import type { CondensedEdge } from '../../../main/resources/viewer/types.ts';
import { loadCorpus } from './corpus.ts';

const drawn = (kinds: CondensedEdge['kinds']): CondensedEdge => ({
  source: 'a', target: 'b', kinds, count: Object.values(kinds).reduce((sum, n) => sum + n, 0),
});

// One kind under the line: its colour and dash still say what it is, so the line needs no word.
test('a condensed edge of one kind keeps that kind\'s colour and dash, and no kind word', () => {
  const order = condensedEdgeStyle(drawn({ ORDER: 1 }));
  assert.deepEqual(order, { colour: edgeStyle('ORDER').colour, label: undefined, dashed: true });
  // The pair: another single kind is told apart by its own colour.
  assert.deepEqual(condensedEdgeStyle(drawn({ TRUE: 1 })), { colour: edgeStyle('TRUE').colour, label: undefined, dashed: false });
});

// Several kinds under one line: no one kind's colour would be true of it, so it is drawn as flow is,
// grey and solid - even when every kind under it is a dashed one.
test('a condensed edge of mixed kinds is neutral and solid', () => {
  assert.deepEqual(condensedEdgeStyle(drawn({ ORDER: 1, EVENT: 2 })), { colour: EDGE_DEFAULT, label: '×3', dashed: false });
  assert.deepEqual(condensedEdgeStyle(drawn({ FLOW: 1, TRUE: 1 })), { colour: EDGE_DEFAULT, label: '×2', dashed: false });
});

test('a condensed edge is captioned ×N iff it stands for more than one edge', () => {
  assert.equal(condensedEdgeStyle(drawn({ EVENT: 5 })).label, '×5');
  assert.equal(condensedEdgeStyle(drawn({ EVENT: 1 })).label, undefined);
  // On the page that read as a tangle: no caption names a kind, and each counts what it stands for.
  const { payload } = loadCorpus().find((c) => c.name === 'boundary')!;
  const edges = condensed(payload, condensedOpening(payload)).edges;
  assert.ok(edges.some((e) => e.count > 1) && edges.some((e) => e.count === 1), 'boundary has no edge of each sort');
  for (const e of edges) assert.equal(condensedEdgeStyle(e).label, e.count > 1 ? `×${e.count}` : undefined);
});
