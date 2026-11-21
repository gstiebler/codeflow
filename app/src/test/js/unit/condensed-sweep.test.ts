import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCorpus } from './corpus.ts';
import { condensed, condensedOpening, groupOf, tapCondensed } from '../../../main/resources/viewer/condensed.ts';
import type { CondensedView, Id, Payload } from '../../../main/resources/viewer/types.ts';

const corpus = loadCorpus();

/**
 * What is wrong with one condensed view of a payload, or nothing: every leaf in exactly one display
 * node - on its own when revealed, counted in its group otherwise - and no drawn edge from a display
 * node to itself.
 */
function faults(payload: Payload, revealed: Set<Id>, view: CondensedView): string[] {
  const found: string[] = [];
  const groups = groupOf(payload);
  const ids = view.nodes.map((n) => n.id);
  if (new Set(ids).size !== ids.length) found.push('a display node is drawn twice');

  const shouldCount = new Map<Id, number>();
  for (const [leaf, group] of groups) {
    if (revealed.has(leaf)) {
      if (!ids.includes(leaf)) found.push(`revealed ${leaf} is not drawn`);
    } else {
      shouldCount.set(group, (shouldCount.get(group) ?? 0) + 1);
    }
  }
  for (const node of view.nodes) {
    if (node.type === 'GROUP') {
      if (node.members !== shouldCount.get(node.id)) found.push(`${node.id} stands for ${node.members}, not ${shouldCount.get(node.id)}`);
    } else if (!revealed.has(node.id)) {
      found.push(`${node.id} is drawn on its own without being revealed`);
    }
  }
  for (const group of shouldCount.keys()) {
    if (!view.nodes.some((n) => n.id === group)) found.push(`${group} has members and is not drawn`);
  }
  for (const edge of view.edges) {
    if (edge.source === edge.target) found.push(`an edge joins ${edge.source} to itself`);
  }
  return found;
}

/**
 * Click every display node on screen, checking the view after each click, until nothing is left in a
 * group. A group click always takes something out, so this ends; the bound is there in case it does
 * not, so a broken click fails rather than hangs.
 */
function walk(payload: Payload): { revealed: Set<Id>; faults: string[] } {
  let revealed = condensedOpening(payload);
  const leaves = payload.nodes.filter((n) => n.type !== 'METHOD').length;
  const problems = faults(payload, revealed, condensed(payload, revealed));
  for (let round = 0; round <= leaves && problems.length === 0; round += 1) {
    const before = condensed(payload, revealed);
    if (!before.nodes.some((n) => n.type === 'GROUP')) break;
    for (const node of before.nodes) {
      revealed = tapCondensed(payload, revealed, { node: node.id });
      problems.push(...faults(payload, revealed, condensed(payload, revealed)));
      if (problems.length > 0) break;
    }
  }
  return { revealed, faults: problems };
}

test('every leaf is in exactly one display node at every step of a click-everything walk', () => {
  const broken = [];
  for (const { name, payload } of corpus) {
    const { faults: found } = walk(payload);
    if (found.length > 0) broken.push(`${name}: ${found.slice(0, 3).join('; ')}`);
  }
  assert.deepEqual(broken, [], broken.join('\n'));
});

// At the end nothing is grouped, so the view is the graph itself: every leaf on its own and every
// payload edge drawn, each (source, target, kind) once with its multiplicity as that kind's count.
test('the walk ends with every leaf on its own and every payload edge drawn once', () => {
  const short = [];
  for (const { name, payload } of corpus) {
    const { revealed } = walk(payload);
    const view = condensed(payload, revealed);
    const leaves = payload.nodes.filter((n) => n.type !== 'METHOD').map((n) => n.id).sort();
    const drawn = view.nodes.map((n) => n.id).sort();
    const want = new Map<string, number>();
    for (const e of payload.edges) want.set(`${e.source}>${e.target}:${e.kind}`, (want.get(`${e.source}>${e.target}:${e.kind}`) ?? 0) + 1);
    const got = new Map(view.edges.flatMap((e) => Object.entries(e.kinds).map(([kind, n]) => [`${e.source}>${e.target}:${kind}`, n])));
    if (JSON.stringify(drawn) !== JSON.stringify(leaves)) short.push(`${name}: ${drawn.length} of ${leaves.length} leaves on their own`);
    else if (JSON.stringify([...got].sort()) !== JSON.stringify([...want].sort())) short.push(`${name}: edges differ`);
  }
  assert.deepEqual(short, [], short.join('\n'));
});

// One group per call-site box, so a boundary method called twice is two groups: `boundary` calls
// boundaryfunc2 and boundaryfunc3 twice each. Each root's event is on its own beside them.
test('a boundary fixture opens on one group per call-site box and each event', () => {
  const opening = (fixture: string) => {
    const { payload } = corpus.find((c) => c.name === fixture)!;
    return condensed(payload, condensedOpening(payload)).nodes.map((n) => `${n.label}:${n.type}`).sort();
  };
  assert.deepEqual(opening('boundary'), [
    'boundaryfunc1:GROUP', 'boundaryfunc2:GROUP', 'boundaryfunc2:GROUP', 'boundaryfunc3:GROUP', 'boundaryfunc3:GROUP',
    'entrypoint:GROUP', 'event entrypoint:EVENT',
  ]);
  const roots = corpus.find((c) => c.name === 'boundaryRoots')!.payload;
  const boxes = roots.nodes.filter((n) => n.type === 'METHOD');
  assert.equal(opening('boundaryRoots').length, boxes.length + 2);
  assert.deepEqual(opening('boundaryRoots').filter((n) => n.endsWith(':EVENT')), ['event first:EVENT', 'event second:EVENT']);
});
