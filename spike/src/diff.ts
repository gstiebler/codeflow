import type { Pair } from './slice.ts';

/**
 * What a change did to the flows through the program.
 *
 * This is the output the whole spike exists to produce. A reviewer reading a Fineract PR can see the
 * lines that changed from `git diff`; what they cannot see is whether a request that used to reach a
 * particular database write still does, or whether a new way to fail appeared. So the comparison is
 * not between two graphs - graphs differ everywhere and prove nothing - but between two sets of
 * boundary pairs, each pair being "this input reaches that output".
 *
 * Four verdicts, and the two in the middle are the ones worth the build:
 *
 * - `NEW` - a flow that did not exist before. A new exception escaping a method lands here.
 * - `GONE` - a flow the change removed. This is the one that catches a value quietly stopping short
 *   of the write it used to reach.
 * - `PATH` - the same input still reaches the same output, but through different code. Invisible to
 *   any "which flows exist" comparison, and often exactly what the PR is about.
 * - `SAME` - unchanged, listed so the report can say what it checked rather than only what it found.
 *
 * A pair is keyed by its source and sink *labels*, never by position, because the endpoint and the
 * repository call are the things a reader recognises across two versions of the code.
 */

export type DiffStatus = 'NEW' | 'GONE' | 'PATH' | 'SAME';

export type DiffRow = {
  status: DiffStatus;
  source: string;
  sink: string;
  before?: Pair;
  after?: Pair;
};

type Print = (node: string) => string;

/**
 * The statements a path runs through, in order.
 *
 * Consecutive repeats are collapsed: at node granularity one source line is many nodes, and a path
 * crossing `Loan t = svc.apply(loan, amount)` touches four of them. A reader compares lines.
 */
export function pathPrint(pair: Pair, fp: Print): string[] {
  const out: string[] = [];
  for (const step of pair.path) {
    const at = fp(step.node);
    if (at !== out[out.length - 1]) out.push(at);
  }
  return out;
}

/** Keyed on what a reader recognises. */
const keyOf = (p: Pair) => `${p.source.label} ${p.sink.label}`;

/** One pair per key, preferring the shortest witness - the clearest one to show. */
function index(pairs: Pair[]): Map<string, Pair> {
  const out = new Map<string, Pair>();
  for (const p of pairs) {
    const seen = out.get(keyOf(p));
    if (!seen || p.path.length < seen.path.length) out.set(keyOf(p), p);
  }
  return out;
}

const RANK: Record<DiffStatus, number> = { NEW: 0, GONE: 1, PATH: 2, SAME: 3 };

export function diffPairs(before: Pair[], after: Pair[], fpBefore: Print, fpAfter: Print): DiffRow[] {
  const was = index(before);
  const now = index(after);

  const rows: DiffRow[] = [];
  for (const [key, pair] of was) {
    const match = now.get(key);
    if (!match) {
      rows.push({ status: 'GONE', source: pair.source.label, sink: pair.sink.label, before: pair });
      continue;
    }
    const moved = pathPrint(pair, fpBefore).join(' | ') !== pathPrint(match, fpAfter).join(' | ');
    rows.push({
      status: moved ? 'PATH' : 'SAME',
      source: pair.source.label,
      sink: pair.sink.label,
      before: pair,
      after: match,
    });
  }
  for (const [key, pair] of now) {
    if (was.has(key)) continue;
    rows.push({ status: 'NEW', source: pair.source.label, sink: pair.sink.label, after: pair });
  }

  return rows.sort(
    (a, b) => RANK[a.status] - RANK[b.status] || `${a.source}${a.sink}`.localeCompare(`${b.source}${b.sink}`),
  );
}
