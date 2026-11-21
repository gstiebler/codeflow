import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadCorpus } from './corpus.ts';
import { condensed, condensedOpening, groupOf, tapChoices, tapCondensed } from '../../../main/resources/viewer/condensed.ts';
import type { CondensedView, Id, Payload } from '../../../main/resources/viewer/types.ts';

const corpus = loadCorpus();

/**
 * What is wrong with one condensed view of a payload, or nothing: every leaf in exactly one display
 * node - on its own when revealed, counted in its group otherwise - and no drawn edge from a display
 * node to itself.
 */
function faults(payload: Payload, revealed: Set<Id>, view: CondensedView): string[] {
  const found: string[] = [];
  const groups = groupOf(payload, revealed);
  const ids = view.nodes.map((n) => n.id);
  if (new Set(ids).size !== ids.length) found.push('a display node is drawn twice');

  const shouldCount = new Map<Id, number>();
  for (const group of groups.values()) shouldCount.set(group, (shouldCount.get(group) ?? 0) + 1);
  const isLeaf = (id: Id) => payload.nodes.some((n) => n.id === id && n.type !== 'METHOD');
  for (const leaf of revealed) {
    // A method id in `revealed` is an open box, not a leaf.
    if (isLeaf(leaf) && !ids.includes(leaf)) found.push(`revealed ${leaf} is not drawn`);
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

/** How a click is answered: everything it could show, or only the first thing the popup lists. */
type Mode = 'all' | 'first';

/**
 * Click every display node on screen, checking the view after each click, until nothing is left in a
 * group. A group click always takes something out, so this ends; the bound is there in case it does
 * not, so a broken click fails rather than hangs. Boxes opened count as progress as leaves do.
 */
function walk(payload: Payload, mode: Mode = 'all'): { revealed: Set<Id>; faults: string[] } {
  let revealed = condensedOpening(payload);
  const bound = payload.nodes.length;
  const problems = faults(payload, revealed, condensed(payload, revealed));
  let rounds = 0;
  for (; rounds <= bound && problems.length === 0; rounds += 1) {
    const before = condensed(payload, revealed);
    if (!before.nodes.some((n) => n.type === 'GROUP')) break;
    for (const node of before.nodes) {
      const target = { node: node.id };
      if (mode === 'all') revealed = tapCondensed(payload, revealed, target);
      else {
        const [first] = tapChoices(payload, revealed, target);
        revealed = new Set([...revealed, ...(first?.reveal ?? [])]);
        if (first === undefined && [...groupOf(payload, revealed).values()].includes(node.id)) problems.push(`a click on ${node.id} offered nothing`);
      }
      problems.push(...faults(payload, revealed, condensed(payload, revealed)));
      if (problems.length > 0) break;
    }
  }
  if (rounds > bound) problems.push('the walk did not end');
  return { revealed, faults: problems };
}

for (const mode of ['all', 'first'] as const) {
  test(`every leaf is in exactly one display node at every step of a click-everything walk (${mode})`, () => {
    const broken = [];
    for (const { name, payload } of corpus) {
      const { faults: found } = walk(payload, mode);
      if (found.length > 0) broken.push(`${name}: ${found.slice(0, 3).join('; ')}`);
    }
    assert.deepEqual(broken, [], broken.join('\n'));
  });
}

// At the end nothing is grouped, so the view is the graph itself: every leaf on its own and every
// payload edge drawn, each (source, target, kind) once with its multiplicity as that kind's count.
for (const mode of ['all', 'first'] as const) test(`the walk ends with every leaf on its own and every payload edge drawn once (${mode})`, () => {
  const short = [];
  for (const { name, payload } of corpus) {
    const { revealed } = walk(payload, mode);
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

// The opening is the entry box, its direct callees (a boundary method called twice is two groups: `boundary`
// calls boundaryfunc2 and boundaryfunc3 twice each), the event on its own, the entry's return and
// what the event starts in the entry box (here a literal and the three parameters).
test('a boundary fixture opens on the entry box, its direct callees and each event', () => {
  const opening = (fixture: string) => {
    const { payload } = corpus.find((c) => c.name === fixture)!;
    return condensed(payload, condensedOpening(payload)).nodes.map((n) => `${n.label}:${n.type}`).sort();
  };
  assert.deepEqual(opening('boundary'), [
    '0:LITERAL', 'a:FUNC_PARAM', 'b:FUNC_PARAM',
    'boundaryfunc1:GROUP', 'boundaryfunc2:GROUP', 'boundaryfunc2:GROUP', 'boundaryfunc3:GROUP', 'boundaryfunc3:GROUP',
    'c:FUNC_PARAM', 'entrypoint:GROUP', 'entrypoint:RETURN', 'event entrypoint:EVENT',
  ]);
  assert.deepEqual(opening('boundaryRoots'), [
    'a:FUNC_PARAM', 'b:FUNC_PARAM', 'event first:EVENT', 'event second:EVENT', 'first:GROUP', 'first:RETURN',
    'later:FUNC_PARAM', 'load:GROUP', 'save:GROUP', 'save:GROUP', 'second:GROUP', 'second:RETURN',
  ]);
});
