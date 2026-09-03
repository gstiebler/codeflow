import type { Boundary, Model } from './types.ts';
import type { Condensation } from './scc.ts';
import { size } from './scc.ts';
import type { Hit } from './slice.ts';
import type { DiffRow } from './diff.ts';

/**
 * The panel, as text.
 *
 * The whole direction of this tool is an IntelliJ call hierarchy whose edges are values instead of
 * calls, so the output is a tree and not a picture. Three things make it readable, and each of them
 * is a decision rather than a formatting detail:
 *
 * **Shared prefixes merge.** Two outputs reached through the same six statements are one branch that
 * forks at the seventh, not two lists that happen to start alike. Without this, the answer to "what
 * does this line affect?" is a wall in which the interesting difference is buried.
 *
 * **Every row names its carrier.** `[via field Svc.total]` is the column a call hierarchy cannot
 * have, and it is the one the rate-factor bug needed: writer and reader were joined by a field, with
 * no call between them to show in any stack.
 *
 * **Rows are lines, not graph nodes.** The graph deliberately keeps one node per sub-expression -
 * see load.ts for why that is not negotiable - but a reader is looking at source. So consecutive
 * steps that land on the same line fold into one row, carrying the carrier of the edge that entered
 * the line. Without the fold, `Loan loan = repo.findById(id)` prints four times in a row and the two
 * request parameters never share a root, because they are two nodes on one signature line.
 *
 * **A boundary ends its branch.** Not because the walk stopped - it did not, see slice.ts - but
 * because past that point the answer stops being about this program, and a reader who wants to go on
 * is asking a different question.
 */

const OUT = '⇤'; // leftwards bar arrow: a value leaving
const IN = '⇥'; // rightwards bar arrow: a value entering
const OPEN = '▼';
const LEAF = '▶';
const CYCLE = '⟳';

/** `in db LoanRepository.findById` reads as `IN db:in LoanRepository.findById`. */
function tag(b: Boundary): string {
  if (b.kind === 'throw') return `${OUT} ${b.label}`;
  return `${b.dir === 'in' ? IN : OUT} ${b.kind}:${b.dir}  ${b.label}`;
}

type Branch = {
  node: string;
  carrier?: string;
  kids: Map<string, Branch>;
  /** Boundaries that end here. A branch with one of these is not expanded further. */
  ends: Boundary[];
};

function blank(node: string): Branch {
  return { node, kids: new Map(), ends: [] };
}

function label(m: Model, c: Condensation, b: Branch): string {
  const members = size(c, b.node);
  const at = m.stmtOfNode.get(b.node) ?? b.node;
  const head =
    members > 1
      ? `${CYCLE} cycle (${members} stmts)  ${at}`
      : `${at}  ${m.stmts.get(at)?.code ?? m.byId.get(b.node)?.code ?? ''}`;
  return b.carrier ? `${head}   [via ${b.carrier}]` : head;
}

function draw(m: Model, c: Condensation, b: Branch, depth: number, out: string[]): void {
  const pad = '  '.repeat(depth);
  const ends = b.ends.map((e) => `   -> ${tag(e)}`).join('');
  out.push(`${pad}${b.kids.size ? OPEN : LEAF} ${label(m, c, b)}${ends}`);
  // A branch that ends at a boundary is not expanded: whatever else it reaches is a different
  // question, asked by clicking the boundary.
  if (b.ends.length) return;
  for (const kid of b.kids.values()) draw(m, c, kid, depth + 1, out);
}

/** The row a step belongs to: its cycle if it is in one, otherwise its source line. */
function rowOf(m: Model, c: Condensation, node: string): string {
  return size(c, node) > 1 ? node : (m.stmtOfNode.get(node) ?? node);
}

export function tree(hits: Hit[], m: Model, c: Condensation): string {
  const roots = new Map<string, Branch>();
  for (const hit of hits) {
    let level = roots;
    let branch: Branch | undefined;
    let last = '';
    for (const step of hit.path) {
      const row = rowOf(m, c, step.node);
      if (row === last) continue;
      last = row;
      branch = level.get(row);
      if (!branch) {
        branch = blank(step.node);
        branch.carrier = step.via?.carrier;
        level.set(row, branch);
      }
      level = branch.kids;
    }
    if (branch) branch.ends.push(hit.boundary);
  }

  const out: string[] = [];
  for (const root of roots.values()) draw(m, c, root, 0, out);
  return out.join('\n');
}

/**
 * The PR view: what the change did to the flows, loudest first.
 *
 * Only `NEW` and `GONE` are listed. That is a measurement, not a preference. Run over two Fineract
 * commits, every one of the 13 `NEW` and `GONE` rows crossed a file the commit actually changed, and
 * on the single-file commit all 43 `PATH` rows crossed none of it: a `PATH` compares one witness per
 * pair, and which witness that is depends on ties broken far from the diff. The verdict is real -
 * "the same input still reaches the same output, by different code" is the thing a which-flows-exist
 * comparison cannot say - but a single shortest path is not enough evidence to say it with, so the
 * count is reported and the rows are not. See FINDINGS.md.
 *
 * The unchanged rows are counted for the same reason: a reviewer needs to know the check ran over
 * everything, because a report that only prints findings is indistinguishable from a report that
 * found nothing because it looked at nothing.
 */
export function diffReport(rows: DiffRow[]): string {
  const out: string[] = [];
  for (const row of rows) {
    if (row.status !== 'NEW' && row.status !== 'GONE') continue;
    out.push(`${row.status.padEnd(4)} ${row.source} -> ${row.sink}`);
  }
  const count = (s: DiffRow['status']) => rows.filter((r) => r.status === s).length;
  out.push(`${out.length ? '\n' : ''}${rows.length} pairs: ${count('NEW')} new, ${count('GONE')} gone, ${count('PATH')} same endpoints by different code, ${count('SAME')} unchanged`);
  return out.join('\n');
}
