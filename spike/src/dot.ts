import type { Boundary, Model } from './types.ts';
import type { Condensation } from './scc.ts';
import { size } from './scc.ts';
import type { Hit } from './slice.ts';

/**
 * The same slice `render.ts` prints as a tree, as a Graphviz graph.
 *
 * A tree is the right default - it is what a reader of a call hierarchy expects, and it is the shape
 * the design document argues for. But a tree has to duplicate a statement that two flows both pass
 * through, and duplication is exactly what hides a join. So this draws the slice as what it actually
 * is: every statement the walk kept, and *every* edge of the condensation between those statements,
 * not only the ones the witness paths used. Strictly more than the tree shows, laid out by `dot`.
 *
 * One box is one source line, for the reason `render.ts` gives: the graph keeps a node per
 * sub-expression, so drawing nodes puts `WorkingCapitalLoanAllocationRequest.allocationOrder()` on
 * the page eight times in a column and the reader has to work out that they are one statement.
 *
 * Joern ships `joern-export --repr=ddg|pdg|all`, which would have saved writing this, and it does not
 * work here. On the 316-file working-capital CPG every representation dies in `ReachingDefPass` with
 * `SchemaViolationException: OUT edge with label REF ... is mandatory, but not defined for this
 * METHOD_REF node` - a `Foo::bar` reference javasrc2cpg could not resolve, of which Fineract has
 * many. It insists on recomputing the dataflow overlay rather than reading the REACHING_DEF edges
 * already in the file, and that recomputation is what fails. On the six-file fixture, where it does
 * run, it emits three edges for a method our own dump reads thirty-four from. Even had it worked it
 * would draw one method at a time: the value of this picture is that it crosses methods, classes and
 * a field, which is precisely what Joern's per-method DDG cannot show.
 */

const FILL = {
  net: '#dbeafe',
  db: '#dcfce7',
  json: '#fef3c7',
  throw: '#fee2e2',
} as const;

/** Graphviz string literal. Only `"` and `\` are special once newlines are already escaped. */
function quote(s: string): string {
  return `"${s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n')}"`;
}

function clip(s: string, max = 72): string {
  const flat = s.replace(/\s+/g, ' ').trim();
  return flat.length > max ? `${flat.slice(0, max - 1)}…` : flat;
}

/** `Api.java:22` - the path without its directories, which are the same for every node here. */
function where(at: string): string {
  const cut = at.lastIndexOf('/');
  return cut === -1 ? at : at.slice(cut + 1);
}

/** The box a component belongs to: its cycle if it is in one, otherwise its source line. */
function boxOf(m: Model, c: Condensation, node: string): string {
  return size(c, node) > 1 ? node : (m.stmtOfNode.get(node) ?? node);
}

function nodeLabel(m: Model, c: Condensation, node: string): string {
  const members = size(c, node);
  const at = m.stmtOfNode.get(node) ?? node;
  const head = members > 1 ? `⟳ cycle, ${members} stmts` : where(at);
  const code = m.stmts.get(at)?.code ?? m.byId.get(node)?.code ?? '';
  return `${head}\n${clip(code)}`;
}

export function dot(hits: Hit[], m: Model, c: Condensation, title: string, focus: string[] = []): string {
  // What the walk kept, folded to boxes, and which of those a boundary sits on.
  const kept = new Set<string>();
  const box = new Map<string, string>();
  const ends = new Map<string, Boundary[]>();
  const at = (node: string) => {
    let id = box.get(node);
    if (id === undefined) box.set(node, (id = boxOf(m, c, node)));
    return id;
  };
  for (const hit of hits) {
    for (const step of hit.path) kept.add(at(step.node));
    const last = at(hit.path[hit.path.length - 1]!.node);
    if (!ends.has(last)) ends.set(last, []);
    if (!ends.get(last)!.some((b) => b.key === hit.boundary.key)) ends.get(last)!.push(hit.boundary);
  }

  const lines = [
    'digraph slice {',
    `  labelloc="t"; label=${quote(title)}; fontname="Helvetica"; fontsize=14;`,
    '  node [shape=box, style="rounded,filled", fillcolor="#f8fafc", color="#94a3b8", fontname="Helvetica", fontsize=10];',
    '  edge [fontname="Helvetica", fontsize=9, color="#64748b"];',
  ];

  // What was asked about. On a page of forty boxes the title is not enough to find it by.
  const asked = new Set(focus.map((node) => boxOf(m, c, node)));

  for (const node of kept) {
    const boundaries = ends.get(node);
    const head = nodeLabel(m, c, node);
    const attrs = [`label=${quote(boundaries ? `${head}\n${boundaries.map((b) => b.label).join('\n')}` : head)}`];
    if (boundaries) {
      // A boundary box says what it is, because that is the reason the walk stopped there.
      attrs.push(`fillcolor="${FILL[boundaries[0]!.kind]}"`, 'color="#334155"', 'penwidth=1.6');
    }
    if (asked.has(node)) attrs.push('color="#1d4ed8"', 'penwidth=2.4');
    if (size(c, node) > 1) attrs.push('peripheries=2');
    lines.push(`  ${quote(node)} [${attrs.join(', ')}];`);
  }

  // Every edge between kept boxes, not only the ones a witness path used. Edges inside a box are the
  // sub-expressions of one line reaching each other, which is not something a reader of source asks.
  const drawn = new Set<string>();
  for (const [node, from] of box) {
    for (const e of c.out.get(node) ?? []) {
      const to = box.get(e.to);
      if (to === undefined || to === from) continue;
      const key = `${from}|${to}|${e.carrier}`;
      if (drawn.has(key)) continue;
      drawn.add(key);
      const style = e.kind === 'field' ? ', style=dashed' : e.kind === 'guard' ? ', style=dotted' : '';
      lines.push(`  ${quote(from)} -> ${quote(to)} [label=${quote(clip(e.carrier, 28))}${style}];`);
    }
  }

  lines.push('}');
  return lines.join('\n');
}
