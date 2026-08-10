/**
 * The Cytoscape renderer.
 *
 * Kept because it is the second opinion: it derives an edge's visibility from its endpoints and a
 * box's from its descendants, so a page it draws differently from the React Flow one is a fact
 * about model.ts that no single renderer could have told us. --html-cytoscape is what emits it.
 *
 * Nothing here decides what is on screen. That is model.ts, where the tests are.
 */
import cytoscape from 'cytoscape';
import elk from 'cytoscape-elk';
import { opening, screen, tap } from './model.ts';
import { ELK_OPTIONS } from './layout.ts';
import { PALETTE, NODE_DEFAULT, EDGE_COLOURS } from './theme.ts';
import type { Id, NodeType, Payload } from './types.ts';

cytoscape.use(elk);

// The same options the React Flow page hands to ELK directly - one copy, so that spacing or
// direction cannot drift between the two pages. The positions still differ: cytoscape-elk builds its
// own ELK graph from the Cytoscape one, where layout.ts builds it from the view.
export const LAYOUT = { name: 'elk', elk: ELK_OPTIONS };

export function init(payload: Payload) {
  const cy = cytoscape({
    // Not id="cy": the browser publishes a global for every element id, so a container called `cy`
    // would make window.cy the div and any check for the graph being ready pass before it existed.
    container: document.getElementById('graph'),
    elements: {
      // `badge` seeded with the plain name so nothing draws blank in the frame before apply() runs.
      nodes: payload.nodes.map((n) => ({ data: { ...n, badge: n.label } })),
      edges: payload.edges.map((e) => ({ data: e })),
    },
    // `as` because cytoscape's own typings say `padding` is a string, where the runtime has always
    // taken a number - and 6 is what this page draws. A cast is the smaller wrong: the alternative
    // is changing a rendered value to satisfy a declaration file.
    style: ([
      { selector: 'node', style: {
        label: 'data(badge)', 'text-valign': 'center', 'font-size': 11,
        shape: 'round-rectangle', 'background-color': (n: cytoscape.NodeSingular) => PALETTE[n.data('type') as NodeType] ?? NODE_DEFAULT,
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
    ] as cytoscape.StylesheetStyle[]),
    // No layout here: apply() runs one at the end of init, and a second on every click. Laying out
    // in the constructor as well only costs a run nobody sees.
  });

  let revealed: Set<Id> = opening(payload);

  const apply = () => {
    // Everything about what is on screen was decided in model.ts. This writes it.
    const view = screen(payload, revealed);
    const state = new Map(view.nodes.map((node) => [node.id, node]));
    for (const node of cy.nodes()) {
      const drawn = state.get(node.id())!;
      node.data('badge', drawn.badge);
      // Never a box. Cytoscape derives a box's visibility from its descendants, transitively, and a
      // display of ours would hide one whose only visible node is a grandchild, leaving that
      // grandchild nowhere to live. `drawn.visible` says the same thing for boxes - this renderer
      // just has no use for it.
      if (drawn.type === 'METHOD') continue;
      node.style('display', drawn.visible ? 'element' : 'none');
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
  (window as unknown as { cy: cytoscape.Core }).cy = cy;
  return cy;
}
