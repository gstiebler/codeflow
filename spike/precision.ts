/**
 * How much of what the diff reports is actually about the commit.
 *
 * The report itself cannot be graded by reading it: 8138 `PATH` rows on one commit either mean the
 * change was that far-reaching or mean the tool is noisy, and only the commit can settle which. So
 * this asks a question the tool never sees the answer to - does the flow this row is about run
 * through a file the commit edited? - and counts. A row that crosses none of the changed files is a
 * row the commit cannot have caused.
 *
 * Two bars, weak and strong. Weak: the pair's interior touches some changed file. Strong: the two
 * interiors *differ* in a statement of a changed file, which is the row's own claim restricted to
 * the code the author actually wrote. The numbers in FINDINGS.md come from here.
 *
 * Run: node --max-old-space-size=12000 spike/precision.ts before.json after.json <changed files...>
 * The file names are matched by suffix, so paths as `git show --name-only` prints them work once the
 * `src/main/java/` prefix is off.
 */

import { readFileSync } from 'node:fs';
import type { Boundary, Graph, Model } from './src/types.ts';
import { buildModel } from './src/load.ts';
import { condense, type Condensation } from './src/scc.ts';
import { boundaries } from './src/boundaries.ts';
import { allPairs, indexBoundaries } from './src/slice.ts';
import { nodeFingerprinter, setName } from './src/fingerprint.ts';
import { diffPairs } from './src/diff.ts';

/** Per pair key: the files the interior touches, and a name for its statements in changed files. */
function interiorFiles(m: Model, c: Condensation, bs: Boundary[], touched: (f: string) => boolean) {
  const index = indexBoundaries(c, bs);
  const byLabel = new Map<string, Boundary[]>();
  for (const b of bs) {
    if (b.dir !== 'in') continue;
    if (!byLabel.has(b.key)) byLabel.set(b.key, []);
    byLabel.get(b.key)!.push(b);
  }
  const fp = nodeFingerprinter(m);
  const fileOf = new Map<string, string[]>();
  const printOf = new Map<string, string[]>();
  for (const [id, members] of c.members) {
    const files = new Set<string>();
    const prints = new Set<string>();
    for (const node of members) {
      const at = m.stmtOfNode.get(node);
      if (!at) continue;
      const file = m.stmts.get(at)!.file;
      files.add(file);
      if (touched(file)) prints.add(fp(node));
    }
    fileOf.set(id, [...files]);
    printOf.set(id, [...prints]);
  }
  const walkOut = (starts: string[]) => {
    const seen = new Set(starts);
    const queue = [...starts];
    while (queue.length) for (const e of c.out.get(queue.shift()!) ?? []) if (!seen.has(e.to)) { seen.add(e.to); queue.push(e.to); }
    return seen;
  };
  const walkIn = (start: string) => {
    const seen = new Set([start]);
    const queue = [start];
    while (queue.length) for (const e of c.in.get(queue.shift()!) ?? []) if (!seen.has(e.from)) { seen.add(e.from); queue.push(e.from); }
    return seen;
  };
  const found: { key: string; at: string; ahead: Set<string> }[] = [];
  for (const group of byLabel.values()) {
    const starts = [...new Set(group.map((b) => c.componentOf.get(b.node) ?? b.node))];
    const ahead = walkOut(starts);
    for (const at of ahead) for (const sink of index.outAt.get(at) ?? []) found.push({ key: `${group[0]!.label} ${sink.label}`, at, ahead });
  }
  const bySink = new Map<string, typeof found>();
  for (const f of found) { if (!bySink.has(f.at)) bySink.set(f.at, []); bySink.get(f.at)!.push(f); }
  const out = new Map<string, { files: Set<string>; prints: Set<string> }>();
  for (const [at, group] of bySink) {
    const behind = walkIn(at);
    for (const f of group) {
      const seen = out.get(f.key) ?? { files: new Set<string>(), prints: new Set<string>() };
      const [small, large] = f.ahead.size <= behind.size ? [f.ahead, behind] : [behind, f.ahead];
      for (const comp of small) {
        if (!large.has(comp)) continue;
        for (const file of fileOf.get(comp) ?? []) seen.files.add(file);
        for (const print of printOf.get(comp) ?? []) seen.prints.add(print);
      }
      out.set(f.key, seen);
    }
  }
  return out;
}

const changed = process.argv.slice(4);
const touched = (f: string) => changed.some((c) => f.endsWith(c));

const load = (p: string) => {
  const m = buildModel(JSON.parse(readFileSync(p, 'utf8')) as Graph);
  const c = condense(m);
  const bs = boundaries(m);
  return { m, c, bs, pairs: allPairs(m, c, bs), fp: nodeFingerprinter(m), files: interiorFiles(m, c, bs, touched) };
};
const a = load(process.argv[2]!), b = load(process.argv[3]!);

const rows = diffPairs(a.pairs, b.pairs, a.fp, b.fp);
const witness = (pair: { path: { node: string }[] }, m: Model) => {
  const out = new Set<string>();
  for (const s of pair.path) { const at = m.stmtOfNode.get(s.node); if (at) out.add(m.stmts.get(at)!.file); }
  return out;
};
let inside = 0, outside = 0, explained = 0;
for (const r of rows.filter((x) => x.status === 'PATH')) {
  const key = `${r.source} ${r.sink}`;
  const was = a.files.get(key), now = b.files.get(key);
  const seen = new Set([...(was?.files ?? []), ...(now?.files ?? [])]);
  if ([...seen].some(touched)) inside++; else outside++;
  // Stronger: the interiors differ in a statement of a file the commit actually edited.
  if (setName(was?.prints ?? []) !== setName(now?.prints ?? [])) explained++;
}
console.log(`PATH ${inside + outside}: ${inside} cross a file the commit changed, ${outside} cross none; ${explained} differ in one`);
for (const s of ['NEW', 'GONE'] as const) {
  const some = rows.filter((r) => r.status === s);
  const hit = some.filter((r) => [...witness((r.after ?? r.before)!, r.after ? b.m : a.m)].some(touched)).length;
  console.log(`${s}: ${some.length}, of which ${hit} cross a changed file`);
}
console.log(`SAME: ${rows.filter((r) => r.status === 'SAME').length}`);
