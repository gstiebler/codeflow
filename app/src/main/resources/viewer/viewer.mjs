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
    const hidden = hiddenDegree(payload.edges, revealed);
    for (const node of cy.nodes()) {
      // A separate field from `label`, which stays the plain name: the annotation is a property of
      // the current view rather than of the value, and anything looking a node up by what it is
      // called has to go on finding it. No edge names a box, so a box gets its name back unchanged.
      node.data('badge', badgeLabel(node.data('label'), hidden.get(node.id())));
      // Never a box. Cytoscape works a box's visibility out from its descendants, transitively -
      // display:none here would hide a box whose only visible node is a grandchild, and that
      // grandchild would have nowhere to live.
      if (isBox(node)) continue;
      node.style('display', revealed.has(node.id()) ? 'element' : 'none');
    }
    cy.layout(LAYOUT).run();
  };

  cy.on('tap', 'node', (event) => {
    const node = event.target;
    if (isBox(node)) {
      // descendants(), not children(): a box holds boxes, and folding one has to take the lot.
      for (const inside of node.descendants()) {
        if (!isBox(inside)) revealed.delete(inside.id());
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
