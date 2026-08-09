/**
 * The renderer: everything that needs a browser.
 *
 * Never loaded on its own. HtmlExporter substitutes model.mjs into the same module script directly
 * above this one, so `withStubs`, `hiddenDegree` and the rest are free identifiers here rather than
 * imports - an inlined module served from file:// has nothing to resolve an import against, and
 * HtmlExporter is substitution only, so it cannot strip one either.
 *
 * Nothing here decides what is on screen. That is model.mjs, where the tests are.
 */

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

  let revealed = opening(payload);

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
    revealed = tap(payload, revealed, event.target.id());
    apply();
  });

  // Folding needs a box, so a sprawl inside the entry method has nothing to fold. Without this
  // the only way back is a reload, which re-runs the whole layout.
  document.addEventListener('keydown', (event) => {
    if (event.key === 'r' || event.key === 'R') {
      revealed = opening(payload);
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
