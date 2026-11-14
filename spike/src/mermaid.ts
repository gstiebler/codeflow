import { condense } from './condense.ts';
import type { Boundary, Graph, Model, NodeFact } from './types.ts';

/** One endpoint of the fixture: `key` letter, the path under `/cb`, and a title. */
export type Scenario = { key: string; path: string; title: string };

const esc = (s: string) => s.replace(/"/g, '#quot;').replace(/[<>]/g, (c) => (c === '<' ? '#lt;' : '#gt;'));
const cmp = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

/**
 * A node's identity and its Mermaid label: `Class:line:col LABEL<br/>code`. Joern sometimes emits
 * several nodes with the same position, label and code (desugared loops, compound assignments); the
 * second and later get ` #k` after the label, k counting in dump order, which is stable across runs
 * even though node ids are not.
 */
function nodeText(m: Model) {
  const base = (n: NodeFact) => {
    const head = `${n.file.replace(/^.*\//, '').replace('.java', '')}:${n.line}${n.col === undefined ? '' : `:${n.col}`} ${n.label}`;
    return `${esc(head)}<br/>${esc(n.code.replace(/\s+/g, ' ').slice(0, 70))}`;
  };
  const seen = new Map<string, number>();
  const text = new Map<string, string>();
  for (const [id, n] of m.byId) {
    const t = base(n);
    const k = (seen.get(t) ?? 0) + 1;
    seen.set(t, k);
    text.set(id, k === 1 ? t : t.replace('<br/>', ` #${k}<br/>`));
  }
  return (id: string) => text.get(id)!;
}

const edgeLabel = (e: { kind: string; carrier: string }) => esc(`${e.kind}: ${e.carrier}`);

/**
 * The value graph reachable from each scenario's endpoint, as one Mermaid flowchart per scenario.
 *
 * Deterministic across Joern runs, whose node ids are not stable: a node is identified by its text
 * (`Class:line LABEL` + code), so nodes with identical text are merged; Mermaid ids are the index of
 * the text in the sorted list; edges are deduped and sorted by their text. Red is a db sink (`out`
 * boundary); red dashed is a sink on the endpoint method's lines that the walk did not reach. A
 * scenario is FOUND when a sink is reached and GAP when one is missed.
 */
export function scenarioMermaid(g: Graph, m: Model, bs: Boundary[], scenarios: Scenario[]): string {
  const out = new Map<string, typeof m.edges>();
  for (const e of m.edges) (out.get(e.from) ?? out.set(e.from, []).get(e.from)!).push(e);
  const sinks = new Set(bs.filter((b) => b.dir === 'out').map((b) => b.node));
  const text = nodeText(m);

  let md = '# Callbacks fixture: value graph per scenario\n\n'
    + 'Source: `spike/fixtures/callbacks/graph.json`. Nodes are Joern nodes (`Class:line LABEL` + code), '
    + 'edges are codeflow value edges reachable from each endpoint, labelled `kind: carrier`. '
    + 'Red = db sink (`ItemRepository.save` call); red dashed = a sink in the scenario that the walk does NOT reach (the gap). '
    + 'Regenerate with `UPDATE_SNAPSHOTS=1 spike/test/extract-callbacks.sh`.\n';

  for (const { key, path, title } of scenarios) {
    const entry = bs.filter((b) => b.dir === 'in' && b.label === `POST /cb/${path}`).map((b) => b.node);
    const seen = new Set(entry);
    const queue = [...entry];
    const edges = [];
    while (queue.length) {
      for (const e of out.get(queue.shift()!) ?? []) {
        edges.push(e);
        if (!seen.has(e.to)) { seen.add(e.to); queue.push(e.to); }
      }
    }
    const method = m.byId.get(entry[0])!.method;
    const lines = new Set(g.nodes.filter((n) => n.method === method).map((n) => `${n.file}:${n.line}`));
    const missed = [...sinks].filter((id) => !seen.has(id) && lines.has(m.stmtOfNode.get(id)!));

    const reached = new Set([...seen].filter((id) => sinks.has(id)).map(text));
    const gap = new Set(missed.map(text));
    const texts = [...new Set([...seen, ...missed].map(text))].sort(cmp);
    const nid = new Map(texts.map((t, i) => [t, `${key}${i}`]));
    const edgeLines = [...new Set(edges.map((e) => `  ${nid.get(text(e.from))} -->|"${edgeLabel(e)}"| ${nid.get(text(e.to))}`))].sort(cmp);

    md += `\n## ${key}. ${title} (${gap.size ? 'GAP' : reached.size ? 'FOUND' : 'no sink'})\n\n\`\`\`mermaid\nflowchart TD\n`;
    for (const t of texts) {
      md += `  ${nid.get(t)}["${t}"]\n`;
      if (reached.has(t) || gap.has(t)) md += `  style ${nid.get(t)} fill:#fee2e2,stroke:#dc2626${reached.has(t) ? '' : ',stroke-dasharray:5 5'}\n`;
    }
    md += edgeLines.join('\n') + '\n```\n';
  }
  return md;
}

const CLASS_DEFS = `  classDef LITERAL fill:#00FF0030
  classDef VARIABLE fill:#80808030
  classDef BIN_OP fill:#80808080
  classDef FUNC_PARAM fill:#8080FF30
  classDef RETURN fill:#FF808080
  classDef EXTERNAL fill:#FFA50040
  classDef UNMODELLED fill:#FF000030,stroke-dasharray: 4 2
`;

/**
 * The value graph of one Kotlin-engine golden case (`app/src/test/resources/<name>`), condensed (see
 * `condense.ts`) and drawn like the engine's own truth.md: one flat subgraph per method, fields as
 * top-level nodes outside any subgraph, `if` on guard edges. Deterministic across Joern runs: nodes
 * are keyed by (method, kind, name[, line:col]), Mermaid ids follow the sorted keys, lines are sorted.
 * Only nodes with at least one edge are drawn.
 */
export function caseMermaid(name: string, m: Model): string {
  const c = condense(m);
  const linked = new Set(c.edges.flatMap((e) => [e.from, e.to]));
  const nodes = c.nodes.filter((n) => linked.has(n.key)).sort((a, b) => cmp(a.key, b.key));
  const id = new Map(nodes.map((n, i) => [n.key, `n${i}`]));
  const decl = (n: (typeof nodes)[number], pad: string) => `${pad}${id.get(n.key)}["${esc(n.label)}"]:::${n.kind}\n`;
  const methods = [...new Set(nodes.flatMap((n) => (n.method ? [n.method] : [])))].sort(cmp);
  const shortName = (method: string) => m.methodByFullName.get(method)?.name ?? method;
  let md = `# ${name}: spike value graph (condensed)\n\n\`\`\`mermaid\nflowchart TD\n`;
  methods.forEach((method, i) => {
    md += `  subgraph m${i}["${esc(shortName(method))}"]\n`;
    for (const n of nodes) if (n.method === method) md += decl(n, '    ');
    md += '  end\n';
  });
  for (const n of nodes) if (!n.method) md += decl(n, '  ');
  const edgeLines = [...new Set(c.edges.map((e) => `  ${id.get(e.from)} -->${e.label ? `|${e.label}|` : ''} ${id.get(e.to)}`))].sort(cmp);
  return md + edgeLines.map((l) => `${l}\n`).join('') + CLASS_DEFS + '```\n';
}
