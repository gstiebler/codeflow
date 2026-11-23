import { test } from 'node:test';
import assert from 'node:assert/strict';
import ELK from 'elkjs/lib/elk.bundled.js';
import { loadCorpus } from './corpus.ts';
import {
  architectureView, contractExpressions, decisionCaption, decisionIds, highLevelHoverText,
  summaryLines, workflowView,
} from '../../../main/resources/viewer/highlevel.ts';
import { condensedCaption } from '../../../main/resources/viewer/condensed.ts';
import { flatElkGraph, routes } from '../../../main/resources/viewer/layout.ts';
import { condensedMermaid } from '../../../main/resources/viewer/mermaid.ts';
import type { Id, Payload } from '../../../main/resources/viewer/types.ts';

function reachable(edges: Array<{ source: Id; target: Id; kind?: string; kinds?: Record<string, number> }>, start: Id): Set<Id> {
  const adj = new Map<Id, Id[]>();
  for (const e of edges) {
    const isOnlyEvent = e.kind === 'EVENT' || (e.kinds !== undefined && Object.keys(e.kinds).length === 1 && e.kinds.EVENT !== undefined);
    if (isOnlyEvent) continue;
    if (!adj.has(e.source)) adj.set(e.source, []);
    adj.get(e.source)!.push(e.target);
  }
  const seen = new Set<Id>();
  const stack = [start];
  while (stack.length > 0) {
    const cur = stack.pop()!;
    for (const next of adj.get(cur) ?? []) {
      if (!seen.has(next)) {
        seen.add(next);
        stack.push(next);
      }
    }
  }
  return seen;
}

/**
 * Soundness invariant for Item 1 (Expression Contraction):
 * For every fixture in the 60+ corpus, contraction preserves exact non-EVENT reachability between
 * every pair of anchors (no lost paths and no invented paths).
 */
test('expression contraction preserves exact anchor-to-anchor reachability across the entire corpus', () => {
  const corpus = loadCorpus();
  let pairsChecked = 0;
  for (const { name, payload } of corpus) {
    const contracted = contractExpressions(payload, { hideNoise: false });
    const anchorIds = new Set(contracted.anchors.map((a) => a.id));
    for (const a of contracted.anchors) {
      if (a.type === 'EVENT') continue;
      const rawReach = new Set([...reachable(payload.edges, a.id)].filter((id) => anchorIds.has(id) && id !== a.id));
      const conReach = new Set([...reachable(contracted.edges, a.id)].filter((id) => anchorIds.has(id) && id !== a.id));
      pairsChecked += 1;
      assert.deepEqual([...conReach].sort(), [...rawReach].sort(), `${name}: reachability diverged from ${a.id} (${a.label})`);
    }
  }
  assert.ok(pairsChecked > 200, `too few anchors checked: ${pairsChecked}`);
});

test('expression contraction strictly removes literal, arithmetic, and local glue on branching/arithmetic fixtures', () => {
  const corpus = loadCorpus();
  const ternary = corpus.find((c) => c.name === 'ternary')!.payload;
  const rawLeaves = ternary.nodes.filter((n) => n.type !== 'METHOD').length;
  const contracted = contractExpressions(ternary);
  assert.ok(contracted.anchors.length < rawLeaves, `expected fewer anchors (${contracted.anchors.length}) than leaves (${rawLeaves})`);
  assert.ok(contracted.edges.some((e) => e.contractedIds.length > 0), 'expected contracted edges carrying intermediate ids');
});

const SAMPLE: Payload = {
  initialLevel: 'architecture',
  nodes: [
    {
      id: 'b0', label: 'repay', type: 'METHOD', method: 'app.LoanController#repay(app.Loan,int)',
      classRole: 'CONTROLLER', entry: 'POST /loans/repay', source: 'LoanController.java:10:5',
      summary: { reads: ['Loan.balance'], writes: ['Loan.balance'], boundaries: ['DB: save'], throws: ['IllegalArgumentException'], gaps: 0 },
    },
    { id: 'n1', label: 'POST /loans/repay', type: 'EVENT', parent: 'b0', source: 'LoanController.java:10:5' },
    { id: 'n2', label: 'amount', type: 'FUNC_PARAM', parent: 'b0', source: 'LoanController.java:10:30' },
    { id: 'n3', label: '0', type: 'LITERAL', parent: 'b0', source: 'LoanController.java:12:20' },
    { id: 'n4', label: '<=', type: 'BIN_OP', parent: 'b0', source: 'LoanController.java:12:18' },
    { id: 'n5', label: 'if', type: 'BIN_OP', parent: 'b0', source: 'LoanController.java:12:9' },
    { id: 'n6', label: 'info', type: 'EXTERNAL', parent: 'b0', role: 'NOISE', source: 'LoanController.java:11:9' },
    { id: 'n7', label: 'throw IllegalArgumentException', type: 'THROW', lead: 'throw', parent: 'b0', source: 'LoanController.java:13:13' },
    { id: 'n8', label: 'repay', type: 'RETURN', parent: 'b0', source: 'LoanController.java:10:5' },
    {
      id: 'b1', label: 'apply', type: 'METHOD', parent: 'b0', method: 'app.LoanServiceImpl#apply(app.Loan,int)',
      classRole: 'SERVICE', assumed: true, called: 'LoanController.java:15:9', source: 'LoanServiceImpl.java:5:5',
      summary: { reads: ['Loan.balance'], writes: ['Loan.balance'], boundaries: ['DB: save'], throws: [], gaps: 1 },
    },
    { id: 'n9', label: 'balance', type: 'VARIABLE', parent: 'b1', fieldOwner: 'app.Loan', fieldOp: 'read', source: 'LoanServiceImpl.java:6:24' },
    { id: 'n10', label: '-', type: 'BIN_OP', parent: 'b1', source: 'LoanServiceImpl.java:6:32' },
    { id: 'n11', label: 'balance', type: 'VARIABLE', parent: 'b1', fieldOwner: 'app.Loan', fieldOp: 'write', source: 'LoanServiceImpl.java:6:9' },
    { id: 'n12', label: 'apply', type: 'RETURN', parent: 'b1', source: 'LoanServiceImpl.java:5:5' },
    {
      id: 'b2', label: 'save', type: 'METHOD', parent: 'b1', method: 'app.LoanRepository#save(app.Loan)',
      boundary: true, boundaryRole: 'DB', called: 'LoanServiceImpl.java:7:9', source: 'LoanRepository.java:4:5',
      summary: { reads: [], writes: [], boundaries: ['DB: save'], throws: [], gaps: 0 },
    },
    { id: 'n13', label: 'call save', type: 'EXTERNAL', lead: 'call', role: 'DB', parent: 'b2', source: 'LoanRepository.java:4:5' },
  ],
  edges: [
    { source: 'n1', target: 'n2', kind: 'EVENT' },
    { source: 'n1', target: 'n3', kind: 'EVENT' },
    { source: 'n2', target: 'n6', kind: 'FLOW' },
    { source: 'n2', target: 'n4', kind: 'FLOW' },
    { source: 'n3', target: 'n4', kind: 'FLOW' },
    { source: 'n4', target: 'n5', kind: 'CONDITION' },
    { source: 'n2', target: 'n5', kind: 'TRUE' },
    { source: 'n5', target: 'n7', kind: 'FLOW' },
    { source: 'n2', target: 'n10', kind: 'FLOW' },
    { source: 'n9', target: 'n10', kind: 'FLOW' },
    { source: 'n10', target: 'n11', kind: 'FLOW' },
    { source: 'n11', target: 'n13', kind: 'FLOW' },
    { source: 'n11', target: 'n12', kind: 'FLOW' },
    { source: 'n12', target: 'n8', kind: 'FLOW' },
  ],
  lines: {
    'LoanController.java': {
      '10': 'public int repay(Loan loan, int amount) {',
      '11': 'log.info("repay");',
      '12': 'if (amount <= 0) {',
      '13': 'throw new IllegalArgumentException("bad");',
      '15': 'return service.apply(loan, amount);',
    },
    'LoanServiceImpl.java': {
      '6': 'loan.balance = loan.balance - amount;',
    },
  },
};

test('architectureView aggregates Entity state cards, Summary cards, assumed badges, throws, and hides noise', () => {
  const arch = architectureView(SAMPLE, { hideNoise: true });
  const byId = new Map(arch.nodes.map((n) => [n.id, n]));

  // Entrypoint uses annotated HTTP route
  assert.equal(byId.get('n1')?.label, 'POST /loans/repay');
  // Entity state card aggregates writes and reads of app.Loan
  const entity = byId.get('entity:app.Loan');
  assert.ok(entity, 'expected Loan entity card');
  assert.equal(entity.type, 'ENTITY');
  assert.ok(entity.subtitle?.some((s) => s.includes('writes: balance')));
  assert.ok(entity.subtitle?.some((s) => s.includes('reads: balance')));
  // Assumed DI service carries assumed flag & summary subtitle
  const svc = byId.get('b1');
  assert.equal(svc?.assumed, true);
  assert.ok(svc?.subtitle?.includes('assumed: single impl'));
  assert.ok(svc?.subtitle?.some((s) => s.includes('writes: Loan.balance')));
  assert.ok(svc?.subtitle?.includes('1 gap'));
  // Throw card is present; NOISE call `info` is hidden
  assert.ok(byId.has('throw:IllegalArgumentException'));
  assert.ok(!arch.nodes.some((n) => n.label.includes('info')));

  // With hideNoise=false, NOISE call reappears
  const withNoise = architectureView(SAMPLE, { hideNoise: false });
  assert.ok(withNoise.nodes.some((n) => n.label.includes('info')));
});

test('workflowView shows decision predicates, folds callees into summary cards, and expands contracted edges', () => {
  const wf = workflowView(SAMPLE, { hideNoise: true });
  const dec = wf.nodes.find((n) => n.id === 'n5');
  assert.equal(dec?.type, 'DECISION');
  assert.equal(dec?.label, 'if: amount <= 0');
  // Callee b1 is folded as a Summary Card with assumed flag
  const callee = wf.nodes.find((n) => n.id === 'b1');
  assert.equal(callee?.type, 'GROUP');
  assert.equal(callee?.assumed, true);
  assert.ok((callee?.subtitle?.length ?? 0) > 0);
  // Noise `info` hidden
  assert.ok(!wf.nodes.some((n) => n.id === 'n6'));

  // Edge amount -> if contracts `<=` (`n4`), and expanding reveals `n4`
  const condEdge = wf.edges.find((e) => e.source === 'n2' && e.target === 'n5');
  assert.ok(condEdge?.contractedIds?.includes('n4'), `expected n4 in contractedIds, got ${JSON.stringify(condEdge)}`);
  const expanded = workflowView(SAMPLE, { hideNoise: true, expanded: new Set(condEdge!.contractedIds) });
  assert.ok(expanded.nodes.some((n) => n.id === 'n4'));

  // Hover on contracted edge advertises expansion
  const tip = highLevelHoverText(SAMPLE, wf, { edge: ['n2', 'n5'] });
  assert.ok(tip.some((line) => line.includes('click to expand')));
});

test('decisionCaption and summaryLines handle missing metadata cleanly', () => {
  assert.equal(decisionIds(SAMPLE).has('n5'), true);
  assert.equal(decisionCaption(SAMPLE, SAMPLE.nodes.find((n) => n.id === 'n5')!), 'if: amount <= 0');
  assert.deepEqual(summaryLines(undefined, true), ['assumed: single impl']);
});

test('ELK lays out and routes Architecture and Workflow views, and Mermaid exports both cleanly', async () => {
  const elk = new ELK();
  for (const view of [architectureView(SAMPLE), workflowView(SAMPLE)]) {
    const laid = await elk.layout(flatElkGraph(view, condensedCaption));
    const routed = routes(laid);
    assert.equal(routed.size, view.edges.length);
    const md = condensedMermaid(view);
    assert.ok(md.startsWith('flowchart TD\n'));
    assert.ok(md.includes('classDef '));
  }
});
