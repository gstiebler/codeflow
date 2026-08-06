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
