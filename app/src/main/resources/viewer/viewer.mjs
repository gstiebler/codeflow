/**
 * Everything the exported page does that is ours.
 *
 * Exports its pure functions so `node --test` can import this file directly, and touches the DOM
 * only from init() - the tests run in Node, where `document` and `cytoscape` do not exist.
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
  const stubOf = new Map();
  for (const node of nodes) {
    if (node.type === 'RETURN' && node.parent && !stubOf.has(node.parent)) stubOf.set(node.parent, node.id);
  }
  const parentOf = (id) => byId.get(id)?.parent;

  const open = new Set();
  for (const id of revealed) {
    const node = byId.get(id);
    if (!node || isBoxNode(node)) continue;
    let box = node.parent;
    // Its own RETURN is how a closed method is drawn, so seeing one is not seeing the body: it
    // leaves that box shut, and only opens the boxes further up that it sits inside.
    if (stubOf.get(box) === id) box = parentOf(box);
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
    if (stubOf.has(box.id)) showing.add(stubOf.get(box.id));
  }
  return showing;
}

const PALETTE = {
  // The Mermaid classDef colours, as rgba. OBJ_VARIABLE and MEM_SPACE have no classDef today and
  // render unstyled there; they get explicit colours here rather than silently sharing one.
  LITERAL:      'rgba(0, 255, 0, 0.19)',
  VARIABLE:     'rgba(128, 128, 128, 0.19)',
  OBJ_VARIABLE: 'rgba(128, 200, 128, 0.25)',
  BIN_OP:       'rgba(128, 128, 128, 0.50)',
  FUNC_PARAM:   'rgba(128, 128, 255, 0.19)',
  RETURN:       'rgba(255, 128, 128, 0.50)',
  EXTERNAL:     'rgba(255, 165, 0, 0.25)',
  MEM_SPACE:    'rgba(200, 200, 128, 0.25)',
  // Where codeflow stopped rather than something the code does - see GraphNode.Unmodelled.
  UNMODELLED:   'rgba(255, 0, 0, 0.19)',
  METHOD:       'rgba(240, 240, 240, 0.60)',
};

// The same strokes MermaidExporter uses, so one graph does not change colour between the two
// renderings. FLOW is absent on purpose - it keeps the default grey, and it is nearly every edge.
const EDGE_COLOURS = {
  TRUE:      '#2e7d32',
  FALSE:     '#c62828',
  CONDITION: '#6a6a6a',
};

export const LAYOUT = {
  name: 'elk',
  elk: {
    algorithm: 'layered',
    'elk.direction': 'DOWN',
    'elk.layered.spacing.nodeNodeBetweenLayers': 40,
    'elk.spacing.nodeNode': 25,
    // Without this ELK lays out each container independently and the boxes overlap.
    'elk.hierarchyHandling': 'INCLUDE_CHILDREN',
  },
};

export function init(payload) {
  const cy = cytoscape({
    // Not id="cy": the browser publishes a global for every element id, so a container called `cy`
    // would make window.cy the div and any check for the graph being ready pass before it existed.
    container: document.getElementById('graph'),
    elements: {
      // `badge` seeded with the plain name so nothing draws blank in the frame before apply() runs.
      nodes: payload.nodes.map((n) => ({ data: { ...n, badge: n.label } })),
      edges: payload.edges.map((e) => ({ data: e })),
    },
    style: [
      { selector: 'node', style: {
        label: 'data(badge)', 'text-valign': 'center', 'font-size': 11,
        shape: 'round-rectangle', 'background-color': (n) => PALETTE[n.data('type')] ?? '#ddd',
        'border-width': 1, 'border-color': '#999', width: 'label', padding: 6,
      } },
      { selector: ':parent', style: {
        'text-valign': 'top', 'font-weight': 'bold', 'background-opacity': 0.35,
      } },
      { selector: 'edge', style: {
        width: 1.5, 'line-color': '#999', 'target-arrow-color': '#999',
        'target-arrow-shape': 'triangle', 'curve-style': 'bezier',
      } },
      // A choice takes three things that are not interchangeable - the value if the test held, the
      // value if it did not, and the test - and three identical arrows say only "one of these".
      // Everything else is FLOW and keeps the grey above, which is nearly every edge on the page.
      ...Object.entries(EDGE_COLOURS).map(([kind, colour]) => ({
        selector: `edge[kind = "${kind}"]`,
        style: { 'line-color': colour, 'target-arrow-color': colour, label: kind.toLowerCase() },
      })),
      { selector: 'edge[kind = "CONDITION"]', style: { 'line-style': 'dashed', label: 'if' } },
    ],
    // No layout here: apply() runs one at the end of init, and a second on every click. Laying out
    // in the constructor as well only costs a run nobody sees.
  });

  const isBox = (node) => node.data('type') === 'METHOD';
  const entryBox = cy.nodes('[type = "METHOD"]').filter((n) => n.isOrphan());
  // The entry method's own values, and nothing from anything it calls.
  const opening = () => new Set(
    entryBox.children().filter((n) => !isBox(n)).map((n) => n.id()),
  );

  let revealed = opening();

  const apply = () => {
    // What is on screen is the reveal set plus one stub per offered callee - derived every time
    // rather than stored, so folding a box cannot strand a stub that was added when it opened.
    const showing = withStubs(payload.nodes, revealed);
    const hidden = hiddenDegree(payload.edges, showing);
    for (const node of cy.nodes()) {
      // A separate field from `label`, which stays the plain name: the annotation is a property of
      // the current view rather than of the value, and anything looking a node up by what it is
      // called has to go on finding it. No edge names a box, so a box gets its name back unchanged.
      node.data('badge', badgeLabel(node.data('label'), hidden.get(node.id())));
      // Never a box. Cytoscape works a box's visibility out from its descendants, transitively -
      // display:none here would hide a box whose only visible node is a grandchild, and that
      // grandchild would have nowhere to live.
      if (isBox(node)) continue;
      node.style('display', showing.has(node.id()) ? 'element' : 'none');
    }
    cy.layout(LAYOUT).run();
  };

  cy.on('tap', 'node', (event) => {
    const node = event.target;
    if (isBox(node)) {
      // A box click is one toggle over the other axis of the graph. Dataflow is a forest - every
      // fixture in the suite is disconnected - so a callee reached by a call that passes no value
      // has no edge into it from anywhere, and clicking values could never arrive there.
      const inside = node.descendants().filter((n) => !isBox(n) && revealed.has(n.id()));
      if (inside.length === 0) {
        for (const id of ownLeaves(payload.nodes, node.id())) revealed.add(id);
      } else {
        // descendants(), not children(): a box holds boxes, and folding one has to take the lot.
        for (const gone of node.descendants()) {
          if (!isBox(gone)) revealed.delete(gone.id());
        }
      }
    } else {
      for (const id of neighbourhood(payload.edges, node.id(), REVEAL_DEPTH)) revealed.add(id);
    }
    apply();
  });

  // Folding needs a box, so a sprawl inside the entry method has nothing to fold. Without this
  // the only way back is a reload, which re-runs the whole layout.
  document.addEventListener('keydown', (event) => {
    if (event.key === 'r' || event.key === 'R') {
      revealed = opening();
      apply();
    }
  });

  apply();

  // The browser tests read the graph off this. Nothing in the page uses it.
  window.cy = cy;
  return cy;
}

// Node imports this file to test the pure functions; only a browser has a document to draw into.
// A module's exports are not global, so the template's bare init(...) call needs this.
if (typeof window !== 'undefined') {
  window.init = init;
}
