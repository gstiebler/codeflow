import { readFileSync } from 'node:fs';
import type { Graph } from './types.ts';
import { buildModel } from './load.ts';
import { condense } from './scc.ts';
import { boundaries } from './boundaries.ts';
import { allPairs, backward, forward, indexBoundaries } from './slice.ts';
import { nodeFingerprinter } from './fingerprint.ts';
import { diffPairs } from './diff.ts';
import { diffReport, tree } from './render.ts';
import { dot } from './dot.ts';

/**
 * The spike's front door.
 *
 * Four verbs, no options, no configuration. The point of the spike is to find out whether the
 * boundary diff says anything useful about a real Fineract commit, and every knob added here is a
 * way of tuning the answer until it looks good - which is exactly the failure mode a spike is
 * supposed to avoid. Anything that goes wrong throws: a stack trace naming the graph that broke is
 * more use than a message that swallowed it.
 */

const USAGE = `usage:
  cli.ts forward <graph.json> <File.java:123>   what the values on this line reach
  cli.ts backward <graph.json> <label>          where the values reaching this boundary came from
  cli.ts boundaries <graph.json>                every boundary the rules found
  cli.ts boundary-diff <before.json> <after.json>
  cli.ts dot forward|backward <graph.json> <File.java:123 | label>
                                                the same slice as a Graphviz graph, on stdout`;

function analyse(path: string) {
  const g: Graph = JSON.parse(readFileSync(path, 'utf8'));
  const m = buildModel(g);
  const c = condense(m);
  const bs = boundaries(m);
  return { m, c, bs, index: indexBoundaries(c, bs) };
}

const [verb, ...rest] = process.argv.slice(2);

if (verb === 'forward') {
  const [file, stmt] = rest;
  if (!file || !stmt) throw new Error(USAGE);
  const { m, c, index } = analyse(file);
  const starts = [...m.stmtOfNode].filter(([, at]) => at === stmt).map(([id]) => id);
  if (!starts.length) throw new Error(`no statement ${stmt} in ${file}`);
  console.log(tree(forward(c, index, starts), m, c));
} else if (verb === 'backward') {
  const [file, label] = rest;
  if (!file || !label) throw new Error(USAGE);
  const { m, c, bs, index } = analyse(file);
  const targets = bs.filter((b) => b.label.includes(label));
  if (!targets.length) throw new Error(`no boundary matching ${label} in ${file}`);
  const hits = backward(c, index, targets.map((b) => b.node));
  // Rooted at what was asked about, so the tree reads outwards from the output towards its sources.
  console.log(tree(hits.map((h) => ({ ...h, path: [...h.path].reverse() })), m, c));
} else if (verb === 'dot') {
  // The same two questions as `forward` and `backward`, drawn instead of printed. Piped, not written:
  // `... | dot -Tsvg > slice.svg` is one step, and which format to render is not this tool's business.
  const [direction, file, target] = rest;
  if (!file || !target || (direction !== 'forward' && direction !== 'backward')) throw new Error(USAGE);
  const { m, c, bs, index } = analyse(file);
  if (direction === 'forward') {
    const starts = [...m.stmtOfNode].filter(([, at]) => at === target).map(([id]) => id);
    if (!starts.length) throw new Error(`no statement ${target} in ${file}`);
    console.log(dot(forward(c, index, starts), m, c, bs, `forward from ${target}`, starts));
  } else {
    const targets = bs.filter((b) => b.label.includes(target));
    if (!targets.length) throw new Error(`no boundary matching ${target} in ${file}`);
    const hits = backward(c, index, targets.map((b) => b.node));
    const drawn = hits.map((h) => ({ ...h, path: [...h.path].reverse() }));
    console.log(dot(drawn, m, c, bs, `backward from ${target}`, targets.map((b) => b.node)));
  }
} else if (verb === 'boundaries') {
  const [file] = rest;
  if (!file) throw new Error(USAGE);
  const { bs } = analyse(file);
  for (const b of bs) console.log(`${b.dir} ${b.kind}\t${b.label}\t${b.stmt}`);
} else if (verb === 'boundary-diff') {
  const [beforePath, afterPath] = rest;
  if (!beforePath || !afterPath) throw new Error(USAGE);
  const before = analyse(beforePath);
  const after = analyse(afterPath);
  const rows = diffPairs(
    allPairs(before.m, before.c, before.bs),
    allPairs(after.m, after.c, after.bs),
    nodeFingerprinter(before.m),
    nodeFingerprinter(after.m),
  );
  console.log(diffReport(rows));
} else {
  throw new Error(USAGE);
}
