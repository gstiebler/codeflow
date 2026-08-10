/**
 * The renderer, and what --html emits.
 *
 * It derives nothing. Whether an edge is drawn, whether a box is drawn, which leaves are showing and
 * what each one is captioned are all fields on the view, and this file writes them out. That is the
 * whole boundary: a decision made here is a decision no test can reach without a browser.
 *
 * Nothing here decides what is on screen. That is model.ts, where the tests are.
 */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Background, Handle, MarkerType, Position, ReactFlow,
  type Edge, type Node, type NodeProps, type ReactFlowInstance,
} from '@xyflow/react';
import css from '@xyflow/react/dist/style.css';
import ELK from 'elkjs/lib/elk.bundled.js';
import { opening, screen, tap } from './model.ts';
import { elkGraph, positions, NODE_HEIGHT, TITLE_HEIGHT, type Positioned } from './layout.ts';
import { PALETTE, NODE_DEFAULT, EDGE_COLOURS, EDGE_DEFAULT } from './theme.ts';
import type { Id, Payload, View, ViewNode } from './types.ts';

/** The stylesheet arrives as text so the page stays one file. */
function installStyles() {
  const style = document.createElement('style');
  style.textContent = `${css}
    .cf-node { border: 1px solid #999; border-radius: 4px; font: 11px system-ui, sans-serif;
               display: flex; align-items: center; justify-content: center; box-sizing: border-box;
               width: 100%; height: 100%; }
    .cf-box { border: 1px solid #999; border-radius: 4px; background: ${PALETTE.METHOD};
              box-sizing: border-box; width: 100%; height: 100%; }
    .cf-box > .cf-title { font: bold 11px system-ui, sans-serif; padding: 0 6px; box-sizing: border-box;
                          height: ${TITLE_HEIGHT}px; line-height: ${TITLE_HEIGHT}px; }
    .react-flow__handle { opacity: 0; }`;
  document.head.appendChild(style);
}

/**
 * A leaf. The caption is `badge`, never `label`: the hidden-neighbour counts are a property of the
 * current view rather than of the value, and every test that looks a node up by name needs the name
 * to go on being there.
 *
 * The handles are invisible and exist because React Flow will not draw an edge without them.
 */
function LeafNode({ data }: NodeProps) {
  const node = data as unknown as ViewNode;
  return (
    <div className="cf-node" style={{ background: PALETTE[node.type] ?? NODE_DEFAULT }}>
      <Handle type="target" position={Position.Top} />
      {node.badge}
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

/** A method. Captioned at the top, so an open box reads as a container rather than as a value. */
function BoxNode({ data }: NodeProps) {
  const node = data as unknown as ViewNode;
  return <div className="cf-box"><div className="cf-title">{node.label}</div></div>;
}

const NODE_TYPES = { leaf: LeafNode, box: BoxNode };

function edgesOf(view: View): Edge[] {
  return view.edges.filter((edge) => edge.visible).map((edge) => {
    const colour = EDGE_COLOURS[edge.kind] ?? EDGE_DEFAULT;
    return {
      id: `${edge.source}->${edge.target}`,
      source: edge.source,
      target: edge.target,
      // CONDITION reads `if` rather than `condition`, and is dashed, exactly as the Mermaid document
      // draws it. FLOW is unlabelled: it is nearly every edge.
      label: edge.kind === 'CONDITION' ? 'if' : (edge.kind === 'FLOW' ? undefined : edge.kind.toLowerCase()),
      style: { stroke: colour, strokeWidth: 1.5, ...(edge.kind === 'CONDITION' ? { strokeDasharray: '4 3' } : {}) },
      markerEnd: { type: MarkerType.ArrowClosed, color: colour },
    };
  });
}

/**
 * Boxes first, then leaves.
 *
 * React Flow requires a parent to appear before its children in the array and silently drops a child
 * that arrives first. Sorting by depth is what guarantees it for a box nested inside a box.
 */
function nodesOf(view: View, laid: Positioned[]): Node[] {
  const at = new Map(laid.map((p) => [p.id, p]));
  const byId = new Map(view.nodes.map((n) => [n.id, n]));
  const depth = (id: Id): number => {
    const parent = byId.get(id)?.parent;
    return parent ? depth(parent) + 1 : 0;
  };

  return view.nodes
    .filter((node) => node.visible && at.has(node.id))
    .sort((a, b) => depth(a.id) - depth(b.id))
    .map((node) => {
      const p = at.get(node.id)!;
      return {
        id: node.id,
        type: node.type === 'METHOD' ? 'box' : 'leaf',
        position: { x: p.x, y: p.y },
        data: node as unknown as Record<string, unknown>,
        ...(node.parent ? { parentId: node.parent, extent: 'parent' as const } : {}),
        style: { width: p.width, height: p.height || NODE_HEIGHT },
        draggable: false,
      };
    });
}

function Graph({ payload }: { payload: Payload }) {
  const [revealed, setRevealed] = useState<Set<Id>>(() => opening(payload));
  const [laid, setLaid] = useState<Positioned[]>([]);
  const [flow, setFlow] = useState<ReactFlowInstance | null>(null);
  const view = useMemo(() => screen(payload, revealed), [payload, revealed]);

  useEffect(() => {
    let current = true;
    // No cast either way: layout.ts's ElkNode is assignable to the library's, and what comes back is
    // assignable to positions(). Keeping the two types structurally compatible is what lets layout.ts
    // stay importable by `node --test`, which cannot load elkjs.
    new ELK().layout(elkGraph(view)).then((result) => {
      if (current) setLaid(positions(result));
    });
    return () => { current = false; };
  }, [view]);

  // Refit after every layout. `fitView` on the component alone fits the first drawing only, and
  // opening a box makes the graph several times bigger, so without this a click puts what it
  // revealed off the edge of the screen. A reveal the reader cannot see is the one thing progressive
  // reveal cannot afford.
  //
  // The counter is for the browser tests, which have no other way to know a click has finished:
  // the view changes at once and the drawing a layout later. It counts *layouts* and not renders
  // on purpose - a probe that waited for the page to agree with its own `view` would be asking the
  // renderer whether the renderer is right, and a page drawing every node would hang rather than
  // fail. Nothing in the page reads it.
  useEffect(() => {
    flow?.fitView();
    const w = window as unknown as { drawn?: number };
    w.drawn = (w.drawn ?? 0) + 1;
  }, [flow, laid]);

  // Folding needs a box, so a sprawl inside the entry method has nothing to fold. Without this the
  // only way back is a reload, which re-runs the whole layout.
  useEffect(() => {
    const reset = (event: KeyboardEvent) => {
      if (event.key === 'r' || event.key === 'R') setRevealed(opening(payload));
    };
    document.addEventListener('keydown', reset);
    return () => document.removeEventListener('keydown', reset);
  }, [payload]);

  const onNodeClick = useCallback(
    (_: unknown, node: Node) => setRevealed((was) => tap(payload, was, node.id)),
    [payload],
  );

  const nodes = useMemo(() => nodesOf(view, laid), [view, laid]);
  const edges = useMemo(() => edgesOf(view), [view]);

  // Something to inspect from the console when a page looks wrong. The browser tests deliberately do
  // not read it: a page whose view is right and whose drawing is not is exactly what they are for.
  (window as unknown as { view: View }).view = view;

  return (
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={NODE_TYPES} onNodeClick={onNodeClick}
               onInit={setFlow} fitView minZoom={0.05} proOptions={{ hideAttribution: true }}>
      <Background />
    </ReactFlow>
  );
}

export function init(payload: Payload) {
  installStyles();
  createRoot(document.getElementById('graph')!).render(<Graph payload={payload} />);
}
