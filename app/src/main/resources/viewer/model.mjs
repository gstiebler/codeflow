/**
 * What the viewer puts on screen, decided without a browser.
 *
 * No imports, no DOM, no Cytoscape: `node --test` imports this file directly, and the exported page
 * gets it by concatenation - HtmlExporter substitutes it into the same module script as viewer.mjs,
 * because an inlined `<script type="module">` served from file:// has nothing to resolve an import
 * against. `export` inside an inline module is legal and simply unused.
 *
 * Everything here is a pure function of (payload, revealed). That is the point: the three decisions
 * that used to live inside Cytoscape traversals - the opening view, what a click does, and which
 * nodes get a display - are the ones a reader most needs to be right, and were the only ones no
 * test could reach.
 */

/** How far a click reaches. Three hops is enough to cross a call and land in the callee's body. */
export const REVEAL_DEPTH = 3;

/**
 * Every node within `depth` edges of `startId`, following edges in either direction.
 *
 * Breadth-first, and that matters: the walk is bounded, so a node reached by a long path before
 * a short one would be recorded at the wrong distance, and anything past it pruned away. Popping
 * from the end of the queue instead is harmless only for an unbounded walk.
 *
 * `distance` is also what makes this terminate, since the graph has cycles wherever a loop feeds
 * a variable back into itself.
 */
export function neighbourhood(edges, startId, depth) {
  const adjacent = new Map();
  const link = (from, to) => {
    if (!adjacent.has(from)) adjacent.set(from, []);
    adjacent.get(from).push(to);
  };
  for (const edge of edges) {
    link(edge.source, edge.target);
    link(edge.target, edge.source);
  }

  const distance = new Map([[startId, 0]]);
  const queue = [startId];
  // Index rather than shift(): same order, without re-indexing the array on every step.
  for (let head = 0; head < queue.length; head += 1) {
    const id = queue[head];
    if (distance.get(id) === depth) continue;
    for (const next of adjacent.get(id) ?? []) {
      if (!distance.has(next)) {
        distance.set(next, distance.get(id) + 1);
        queue.push(next);
      }
    }
  }
  return new Set(distance.keys());
}

/**
 * How many edges at each revealed node lead somewhere the reader cannot see, per direction.
 *
 * A hidden node is drawn with `display:none`, and Cytoscape drops an edge when either endpoint
 * goes - so without this a node with six hidden neighbours renders identically to a genuine source
 * or sink, and a value arriving from somewhere invisible reads as a value arriving from nowhere.
 *
 * An edge is missing *at* a node only when the node is on screen and the other end is not. Both
 * ends revealed is nothing missing; both ends hidden belongs to neither, and counting it at both
 * would annotate nodes nobody can see. So the two cases are one test: the endpoints agreeing means
 * there is nothing to say.
 *
 * One pass over every edge for the whole graph, rather than a walk per node - which is what rules
 * out counting reachable nodes instead. See the design note for the rest of that argument.
 */
export function hiddenDegree(edges, revealed) {
  const hidden = new Map();
  const count = (id, direction) => {
    if (!hidden.has(id)) hidden.set(id, { in: 0, out: 0 });
    hidden.get(id)[direction] += 1;
  };

  for (const edge of edges) {
    const fromShowing = revealed.has(edge.source);
    if (fromShowing === revealed.has(edge.target)) continue;
    if (fromShowing) count(edge.source, 'out');
    else count(edge.target, 'in');
  }
  return hidden;
}

/**
 * A node's name, with what is missing around it.
 *
 * `undefined` is the ordinary case rather than an error: [hiddenDegree] records only the nodes with
 * something hidden, so a fully surrounded node gets its bare name back through the same path a node
 * with two zeroes does. A zero is never rendered - `amount ↑0 ↓3` reads as though something were
 * being denied, where `amount ↓3` says only what is true.
 */
export function badgeLabel(name, hidden) {
  const parts = [];
  if (hidden?.in > 0) parts.push(`↑${hidden.in}`);
  if (hidden?.out > 0) parts.push(`↓${hidden.out}`);
  return parts.length === 0 ? name : `${name} ${parts.join(' ')}`;
}

const isBoxNode = (node) => node.type === 'METHOD';

/** The leaves directly inside a box - what clicking that box opens, one level and no deeper. */
export function ownLeaves(nodes, boxId) {
  return new Set(nodes.filter((n) => n.parent === boxId && !isBoxNode(n)).map((n) => n.id));
}

/**
 * The one leaf that stands for each box: its RETURN node, which carries the method's name.
 *
 * Extracted so that "what is a stub" has a single definition. [withStubs] decides whether to offer
 * one and [tap] decides what clicking one does, and the two disagreeing would put a node on screen
 * that does nothing when pressed - which is the bug this whole change exists to fix.
 */
export function stubOf(nodes) {
  const stubs = new Map();
  for (const node of nodes) {
    if (node.type === 'RETURN' && node.parent && !stubs.has(node.parent)) {
      stubs.set(node.parent, node.id);
    }
  }
  return stubs;
}

/**
 * Every non-box node under `boxId`, however deep - what folding a box has to take.
 *
 * descendants, not children: a box holds boxes, and folding one that leaves a nested method's nodes
 * on screen would draw a callee floating with no caller around it.
 */
export function descendantLeaves(nodes, boxId) {
  const childrenOf = new Map();
  for (const node of nodes) {
    if (!node.parent) continue;
    if (!childrenOf.has(node.parent)) childrenOf.set(node.parent, []);
    childrenOf.get(node.parent).push(node);
  }

  const leaves = new Set();
  const boxes = [boxId];
  while (boxes.length > 0) {
    for (const child of childrenOf.get(boxes.pop()) ?? []) {
      if (isBoxNode(child)) boxes.push(child.id);
      else leaves.add(child.id);
    }
  }
  return leaves;
}

/**
 * What is on screen: everything revealed, plus one stub per callee of an open method.
 *
 * Reveal follows dataflow edges, and a dataflow graph is a forest - every fixture in the suite is
 * disconnected, and the largest component of `member` is 6 of its 23 leaves. So no amount of
 * clicking from any starting node reaches a callee whose call passes no value: `app.func1()` takes
 * no argument and returns none, so not one edge crosses from `main` into `func1`, and 19 of that
 * fixture's 23 leaves could not be reached at all.
 *
 * Containment is the relation that crosses components - it is already what the opening view is
 * built from - and this is what makes it navigable. A box whose parent is open is offered as its
 * RETURN node, which every box has exactly one of: the method's name, or `<init>` for a
 * constructor. That node is the method's result, so a method you have not opened is drawn as the
 * one value it produces.
 *
 * It has to be a real leaf. Cytoscape derives a compound node's visibility from its children and
 * will not draw a parent with none visible, whatever `display` that parent is given - so an empty
 * box cannot be a click target, and the stub is the only thing that puts a closed method on the
 * page.
 *
 * A stub does **not** open the box it stands for. If it did, offering one callee's stub would make
 * that callee open, which would offer its callees' stubs, and one pass would unfold the entire call
 * tree at open - the wall this viewer exists to avoid. Because it does not, one pass is also
 * enough: no stub can ever produce another.
 */
export function withStubs(nodes, revealed) {
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const stubs = stubOf(nodes);
  const parentOf = (id) => byId.get(id)?.parent;

  const open = new Set();
  for (const id of revealed) {
    const node = byId.get(id);
    if (!node || isBoxNode(node)) continue;
    let box = node.parent;
    // Its own RETURN is how a closed method is drawn, so seeing one is not seeing the body: it
    // leaves that box shut, and only opens the boxes further up that it sits inside.
    if (stubs.get(box) === id) box = parentOf(box);
    // Every ancestor, not just the immediate one: a box holding only boxes is open on the strength
    // of a grandchild, the same case the "never hide a METHOD node" rule exists for.
    for (; box; box = parentOf(box)) open.add(box);
  }

  const showing = new Set(revealed);
  for (const box of nodes) {
    if (!isBoxNode(box)) continue;
    // Closed, and offered by an open caller. An open box needs no stub: its own RETURN is among the
    // leaves a box click reveals, and a box opened by following dataflow instead should show what
    // the walk actually reached and nothing more. Stopping at the caller is what keeps one click
    // from unfolding the whole call tree.
    if (open.has(box.id) || !box.parent || !open.has(box.parent)) continue;
    if (stubs.has(box.id)) showing.add(stubs.get(box.id));
  }
  return showing;
}

/**
 * What the page opens on: the entry method's own leaves, and nothing from anything it calls.
 *
 * The entry method is the box with no parent - there is exactly one, since the payload is the root
 * block and everything it reached. Taking the first METHOD in the list instead would open a
 * callee's body whenever the exporter happened to emit one first.
 */
export function opening(payload) {
  const root = payload.nodes.find((node) => isBoxNode(node) && !node.parent);
  return root ? ownLeaves(payload.nodes, root.id) : new Set();
}

/**
 * What one click does, as a set of revealed ids.
 *
 * One function, so that "what does clicking this do" has one answer to test. Returns a new Set and
 * never mutates its argument, so a caller can compare before against after.
 *
 * Clicks union and never subtract. Folding a box is the only thing that takes anything away, which
 * is why the box branch is the only one that deletes.
 */
export function tap(payload, revealed, id) {
  const node = payload.nodes.find((candidate) => candidate.id === id);
  if (!node) return new Set(revealed);

  const next = new Set(revealed);
  if (isBoxNode(node)) {
    const inside = descendantLeaves(payload.nodes, id);
    const open = [...inside].some((leaf) => next.has(leaf));
    if (open) for (const leaf of inside) next.delete(leaf);
    else for (const leaf of ownLeaves(payload.nodes, id)) next.add(leaf);
    return next;
  }

  for (const reached of neighbourhood(payload.edges, id, REVEAL_DEPTH)) next.add(reached);
  return next;
}
