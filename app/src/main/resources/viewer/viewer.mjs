/**
 * Everything the exported page does that is ours.
 *
 * Exports its pure functions so `node --test` can import this file directly, and touches the DOM
 * only from init() - the tests run in Node, where `document` and `cytoscape` do not exist.
 */

/**
 * The clicked node plus everything a value could have come from and everything it can reach.
 *
 * `reached` is what makes this terminate: the graph has cycles wherever a loop feeds a variable
 * back into itself, and following one forever hangs the page with nothing on screen to explain why.
 */
export function traceFrom(edges, startId) {
  const outgoing = new Map();
  const incoming = new Map();
  for (const edge of edges) {
    if (!outgoing.has(edge.source)) outgoing.set(edge.source, []);
    outgoing.get(edge.source).push(edge.target);
    if (!incoming.has(edge.target)) incoming.set(edge.target, []);
    incoming.get(edge.target).push(edge.source);
  }

  const reached = new Set([startId]);
  for (const adjacency of [outgoing, incoming]) {
    const queue = [startId];
    while (queue.length > 0) {
      const id = queue.pop();
      for (const next of adjacency.get(id) ?? []) {
        if (!reached.has(next)) {
          reached.add(next);
          queue.push(next);
        }
      }
    }
  }
  return reached;
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
  METHOD:       'rgba(240, 240, 240, 0.60)',
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
    container: document.getElementById('cy'),
    elements: {
      nodes: payload.nodes.map((n) => ({ data: n })),
      edges: payload.edges.map((e) => ({ data: e })),
    },
    style: [
      { selector: 'node', style: {
        label: 'data(label)', 'text-valign': 'center', 'font-size': 11,
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
      { selector: '.dimmed', style: { opacity: 0.15 } },
      { selector: '.traced', style: { 'border-width': 3, 'border-color': '#d33' } },
    ],
    layout: LAYOUT,
  });

  // The browser tests read the graph off this. Nothing in the page uses it.
  window.cy = cy;
  return cy;
}

// Node imports this file to test the pure functions; only a browser has a document to draw into.
// A module's exports are not global, so the template's bare init(...) call needs this.
if (typeof window !== 'undefined') {
  window.init = init;
}
