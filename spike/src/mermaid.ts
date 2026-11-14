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

/**
 * The whole value graph of one Kotlin-engine golden case (`app/src/test/resources/<name>`): every
 * codeflow edge of its own dump, no boundary walk, since these cases have no endpoints. Same node
 * text, ids and edge sorting as `scenarioMermaid`, so it is deterministic across Joern runs.
 */
export function caseMermaid(name: string, m: Model): string {
  const text = nodeText(m);
  const texts = [...new Set(m.edges.flatMap((e) => [text(e.from), text(e.to)]))].sort(cmp);
  const nid = new Map(texts.map((t, i) => [t, `n${i}`]));
  const edgeLines = [...new Set(m.edges.map((e) => `  ${nid.get(text(e.from))} -->|"${edgeLabel(e)}"| ${nid.get(text(e.to))}`))].sort(cmp);
  return `# ${name}: spike value graph\n\n\`\`\`mermaid\nflowchart TD\n`
    + texts.map((t) => `  ${nid.get(t)}["${t}"]\n`).join('')
    + edgeLines.map((l) => `${l}\n`).join('')
    + '```\n';
}
