import { test } from 'node:test';
import assert from 'node:assert/strict';
import { condensed, condensedCaption, condensedOpening, tapCondensed } from '../../../main/resources/viewer/condensed.ts';
import { condensedMermaid, escapeLabel, hex } from '../../../main/resources/viewer/mermaid.ts';
import {
  BOUNDARY_OUTLINE, GROUP_FILL, GROUP_OUTLINE, NODE_DEFAULT, NODE_OUTLINE, PALETTE, condensedEdgeStyle,
} from '../../../main/resources/viewer/theme.ts';
import type { CondensedView } from '../../../main/resources/viewer/types.ts';
import { loadCorpus } from './corpus.ts';

const NODE_LINE = /^ {2}(n\d+)\["(.*)"\]$/;
const EDGE_LINE = /^ {2}(n\d+) -->(?:\|"(.*)"\|)? (n\d+)$/;
const CLASSDEF_LINE = /^ {2}classDef (c\d+) fill:(#[0-9a-f]+),stroke:(#[0-9a-f]+),stroke-width:(\d)px$/;
const CLASS_LINE = /^ {2}class (n\d+(?:,n\d+)*) (c\d+)$/;
const LINKSTYLE_LINE = /^ {2}linkStyle (\d+) stroke:(#[0-9a-f]+)(,stroke-dasharray:4 3)?$/;

/** The export, read back: what each line says, by sort. Any line of no known sort fails here. */
function parse(text: string) {
  const lines = text.split('\n');
  assert.equal(lines[0], 'flowchart TD');
  assert.equal(lines[lines.length - 1], '', 'the export ends with a newline');
  const nodes: { id: string; label: string }[] = [];
  const edges: { source: string; caption: string | undefined; target: string }[] = [];
  const classDefs = new Map<string, { fill: string; stroke: string; width: number }>();
  const classOf = new Map<string, string>();
  const linkStyles: { index: number; stroke: string; dashed: boolean }[] = [];
  for (const line of lines.slice(1, -1)) {
    let m;
    if ((m = NODE_LINE.exec(line))) nodes.push({ id: m[1], label: m[2] });
    else if ((m = EDGE_LINE.exec(line))) edges.push({ source: m[1], caption: m[2], target: m[3] });
    else if ((m = CLASSDEF_LINE.exec(line))) classDefs.set(m[1], { fill: m[2], stroke: m[3], width: Number(m[4]) });
    else if ((m = CLASS_LINE.exec(line))) for (const n of m[1].split(',')) {
      assert.ok(!classOf.has(n), `${n} is in two classes`);
      classOf.set(n, m[2]);
    }
    else if ((m = LINKSTYLE_LINE.exec(line))) linkStyles.push({ index: Number(m[1]), stroke: m[2], dashed: m[3] !== undefined });
    else assert.fail(`a line of no known sort: ${JSON.stringify(line)}`);
  }
  return { nodes, edges, classDefs, classOf, linkStyles };
}

const boundary = () => loadCorpus().find((c) => c.name === 'boundary')!.payload;

/** Node `i` of the view is `n<i>`, and every line of the export says what the view does. */
function assertMatches(view: CondensedView, text: string) {
  const out = parse(text);
  assert.deepEqual(out.nodes.map((n) => n.id), view.nodes.map((_, i) => `n${i}`));
  assert.deepEqual(out.nodes.map((n) => n.label), view.nodes.map((n) => escapeLabel(condensedCaption(n))));
  const index = new Map(view.nodes.map((n, i) => [n.id, `n${i}`]));
  assert.deepEqual(out.edges, view.edges.map((e) => ({
    source: index.get(e.source), caption: e.count > 1 ? `×${e.count}` : undefined, target: index.get(e.target),
  })));
}

test('the opening of boundary exports its 11 nodes and one line per drawn edge, ×N iff N > 1', () => {
  const payload = boundary();
  const view = condensed(payload, condensedOpening(payload));
  assert.equal(view.nodes.length, 11);
  const text = condensedMermaid(view);
  assertMatches(view, text);
  const { nodes, edges } = parse(text);
  assert.equal(nodes.length, 11);
  assert.equal(edges.length, view.edges.length);
  // Both sorts are there, so the iff is tested both ways.
  assert.ok(edges.some((e) => e.caption !== undefined) && edges.some((e) => e.caption === undefined));
});

test('after a click the export is the new view, not the opening', () => {
  const payload = boundary();
  const opening = condensed(payload, condensedOpening(payload));
  const after = condensed(payload, tapCondensed(payload, condensedOpening(payload), { node: 'b0' }));
  assert.ok(after.nodes.length > opening.nodes.length);
  const text = condensedMermaid(after);
  assertMatches(after, text);
  assert.equal(parse(text).nodes.length, after.nodes.length);
  assert.notEqual(text, condensedMermaid(opening));
});

test('a label with " < > # comes out as Mermaid entities', () => {
  const view: CondensedView = {
    nodes: [
      { id: 'a', label: 'say("<b>" + #x)', type: 'LITERAL' },
      { id: 'b', label: 'plain', type: 'VARIABLE' },
    ],
    edges: [{ source: 'a', target: 'b', kinds: { FLOW: 1 }, count: 1 }],
    frames: [],
  };
  const { nodes } = parse(condensedMermaid(view));
  assert.equal(nodes[0].label, 'say(#quot;#lt;b#gt;#quot; + #35;x)');
  // The pair: a label with none of the four is written as it is.
  assert.equal(nodes[1].label, 'plain');
});

test('every linkStyle index is an edge of the colour and dash the page draws it in', () => {
  const payload = boundary();
  const view = condensed(payload, tapCondensed(payload, condensedOpening(payload), { node: 'b0' }));
  const { edges, linkStyles } = parse(condensedMermaid(view));
  assert.deepEqual(linkStyles.map((s) => s.index), edges.map((_, i) => i));
  for (const { index, stroke, dashed } of linkStyles) {
    const style = condensedEdgeStyle(view.edges[index]);
    assert.equal(stroke, hex(style.colour), `linkStyle ${index}`);
    assert.equal(dashed, style.dashed, `linkStyle ${index}`);
  }
  // Not one colour throughout, or an index pointing at the wrong edge could still match.
  assert.ok(new Set(linkStyles.map((s) => s.stroke)).size > 1, 'every edge is one colour');
});

test('every node is in one class, filled and outlined as the page draws it, one classDef per look', () => {
  const payload = boundary();
  const view = condensed(payload, tapCondensed(payload, condensedOpening(payload), { node: 'b0' }));
  const { classDefs, classOf } = parse(condensedMermaid(view));
  const looks = new Set<string>();
  view.nodes.forEach((node, i) => {
    const def = classDefs.get(classOf.get(`n${i}`)!)!;
    const want = node.type === 'GROUP'
      ? { fill: hex(GROUP_FILL), stroke: hex(node.boundary ? BOUNDARY_OUTLINE : GROUP_OUTLINE), width: 2 }
      : { fill: hex(PALETTE[node.type] ?? NODE_DEFAULT), stroke: hex(NODE_OUTLINE), width: 1 };
    assert.deepEqual(def, want, node.label);
    looks.add(JSON.stringify(want));
  });
  assert.equal(classDefs.size, looks.size);
  assert.ok(looks.size > 2, 'boundary after a click shows too few looks to tell classes apart');
});

test('hex: rgba() as #rrggbbaa, hex as it is, anything else throws', () => {
  assert.equal(hex('rgba(0, 255, 0, 0.19)'), '#00ff0030');
  assert.equal(hex('rgba(123, 31, 162, 0.25)'), '#7b1fa240');
  assert.equal(hex('rgba(240, 240, 240, 0.60)'), '#f0f0f099');
  assert.equal(hex('#ddd'), '#ddd');
  assert.equal(hex('#7b1fa2'), '#7b1fa2');
  assert.throws(() => hex('red'));
});

test('every fixture\'s condensed opening exports, every line of a known sort', () => {
  for (const { name, payload } of loadCorpus()) {
    const view = condensed(payload, condensedOpening(payload));
    assert.doesNotThrow(() => assertMatches(view, condensedMermaid(view)), name);
  }
});
