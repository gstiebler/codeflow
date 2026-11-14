import type { Boundary, Graph, Model } from './types.ts';

/** One endpoint of the fixture: `key` letter, the path under `/cb`, and a title. */
export type Scenario = { key: string; path: string; title: string };

const esc = (s: string) => s.replace(/"/g, '#quot;').replace(/[<>]/g, (c) => (c === '<' ? '#lt;' : '#gt;'));
const cmp = (a: string, b: string) => (a < b ? -1 : a > b ? 1 : 0);

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
  const head = (id: string) => {
    const n = m.byId.get(id)!;
    return `${n.file.replace(/^.*\//, '').replace('.java', '')}:${n.line} ${n.label}`;
  };
  const code = (id: string) => m.byId.get(id)!.code.replace(/\s+/g, ' ').slice(0, 70);
  const text = (id: string) => `${esc(head(id))}<br/>${esc(code(id))}`;

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
    const edgeLines = [...new Set(edges.map((e) => `  ${nid.get(text(e.from))} -->|"${esc(`${e.kind}: ${e.carrier}`)}"| ${nid.get(text(e.to))}`))].sort(cmp);

    md += `\n## ${key}. ${title} (${gap.size ? 'GAP' : reached.size ? 'FOUND' : 'no sink'})\n\n\`\`\`mermaid\nflowchart TD\n`;
    for (const t of texts) {
      md += `  ${nid.get(t)}["${t}"]\n`;
      if (reached.has(t) || gap.has(t)) md += `  style ${nid.get(t)} fill:#fee2e2,stroke:#dc2626${reached.has(t) ? '' : ',stroke-dasharray:5 5'}\n`;
    }
    md += edgeLines.join('\n') + '\n```\n';
  }
  return md;
}
