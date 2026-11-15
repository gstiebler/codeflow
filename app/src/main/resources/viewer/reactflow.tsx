/**
 * The renderer, and what --html emits.
 *
 * It derives nothing. Whether an edge is drawn, whether a box is drawn, which leaves are showing and
 * what each one is captioned are all fields on the view, and this file writes them out. That is the
 * whole boundary: a decision made here is a decision no test can reach without a browser.
 *
 * Nothing here decides what is on screen. That is model.ts, where the tests are.
 */
import { useCallback, useEffect, useMemo, useState, type Dispatch, type SetStateAction } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Background, Handle, MarkerType, Position, ReactFlow,
  type Edge, type Node, type NodeProps, type ReactFlowInstance,
} from '@xyflow/react';
import css from '@xyflow/react/dist/style.css';
import ELK from 'elkjs/lib/elk.bundled.js';
import { captionParts, opening, screen, tap } from './model.ts';
import { condensed, condensedCaption, condensedOpening, hoverText, tapCondensed } from './condensed.ts';
import { elkGraph, flatEdgeId, flatElkGraph, positions, NODE_HEIGHT, TITLE_HEIGHT, type Positioned } from './layout.ts';
import { PALETTE, NODE_DEFAULT, BOUNDARY_OUTLINE, GROUP_OUTLINE, edgeStyle } from './theme.ts';
import type {
  CondensedNode, CondensedTarget, CondensedView, EdgeKind, Id, Payload, View, ViewNode,
} from './types.ts';

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
    .cf-group { border: 2px solid ${GROUP_OUTLINE}; border-radius: 4px; background: #fff;
                font: bold 11px system-ui, sans-serif; display: flex; align-items: center;
                justify-content: center; box-sizing: border-box; width: 100%; height: 100%; }
    .cf-toolbar { position: fixed; top: 8px; left: 8px; z-index: 10; display: flex; gap: 4px; }
    .cf-toolbar button { font: 12px system-ui, sans-serif; padding: 3px 10px; border: 1px solid #999;
                         border-radius: 4px; background: #fff; cursor: pointer; }
    .cf-toolbar button[aria-pressed="true"] { background: #333; color: #fff; }
    .cf-tooltip { position: fixed; z-index: 20; pointer-events: none; background: #fff;
                  border: 1px solid #999; border-radius: 4px; padding: 4px 8px;
                  font: 11px ui-monospace, monospace; white-space: pre; box-shadow: 0 1px 4px #0003; }
    .react-flow__handle { opacity: 0; }`;
  document.head.appendChild(style);
}

/**
 * A leaf, its `lead` - if the payload gave it one - in bold. The caption is `badge`, never `label`: the hidden-neighbour counts are a property of the
 * current view rather than of the value, and every test that looks a node up by name needs the name
 * to go on being there.
 *
 * The handles are invisible and exist because React Flow will not draw an edge without them.
 */
function LeafNode({ data }: NodeProps) {
  const node = data as unknown as ViewNode;
  const caption = captionParts(node);
  return (
    <div className="cf-node" style={{ background: PALETTE[node.type] ?? NODE_DEFAULT }}>
      <Handle type="target" position={Position.Top} />
      {caption.lead !== undefined && <strong>{caption.lead}</strong>}
      {caption.rest}
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
    const { colour, label, dashed } = edgeStyle(edge.kind);
    return {
      id: `${edge.source}->${edge.target}`,
      source: edge.source,
      target: edge.target,
      label,
      style: { stroke: colour, strokeWidth: 1.5, ...(dashed ? { strokeDasharray: '4 3' } : {}) },
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

/** What a view is handed by the page: the payload and its own `revealed`, which the page keeps. */
type Revealing = { payload: Payload; revealed: Set<Id>; setRevealed: Dispatch<SetStateAction<Set<Id>>> };

function Graph({ payload, revealed, setRevealed }: Revealing) {
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

  const onNodeClick = useCallback(
    (_: unknown, node: Node) => setRevealed((was) => tap(payload, was, node.id)),
    [payload, setRevealed],
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

/** A condensed leaf, drawn by LeafNode: its caption is its label, as it has no hidden-neighbour badge. */
const asLeaf = (node: CondensedNode) => ({ ...node, badge: node.label });

/** A group: its caption - label and count - with an outline saying whose it is. */
function GroupNode({ data }: NodeProps) {
  const node = data as unknown as CondensedNode;
  return (
    <div className="cf-group" style={{ borderColor: node.boundary ? BOUNDARY_OUTLINE : GROUP_OUTLINE }}>
      <Handle type="target" position={Position.Top} />
      {condensedCaption(node)}
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
}

// Not `group`: React Flow has a built-in node type of that name.
const CONDENSED_TYPES = { leaf: LeafNode, condensedGroup: GroupNode };

function condensedNodesOf(view: CondensedView, laid: Positioned[]): Node[] {
  const at = new Map(laid.map((p) => [p.id, p]));
  return view.nodes.filter((node) => at.has(node.id)).map((node) => {
    const p = at.get(node.id)!;
    return {
      id: node.id,
      type: node.type === 'GROUP' ? 'condensedGroup' : 'leaf',
      position: { x: p.x, y: p.y },
      data: (node.type === 'GROUP' ? node : asLeaf(node)) as unknown as Record<string, unknown>,
      style: { width: p.width, height: p.height || NODE_HEIGHT },
      draggable: false,
    };
  });
}

/** As the boxed view draws an edge, with `×N` after the kind's caption when it stands for several. */
function condensedEdgesOf(view: CondensedView): Edge[] {
  return view.edges.map((edge) => {
    const { colour, label, dashed } = edgeStyle(edge.kind);
    const caption = [label, edge.count > 1 ? `×${edge.count}` : undefined].filter((s) => s !== undefined).join(' ');
    return {
      id: flatEdgeId(edge),
      source: edge.source,
      target: edge.target,
      data: { kind: edge.kind },
      ...(caption === '' ? {} : { label: caption }),
      style: { stroke: colour, strokeWidth: 1.5, ...(dashed ? { strokeDasharray: '4 3' } : {}) },
      markerEnd: { type: MarkerType.ArrowClosed, color: colour },
    };
  });
}

type Hover = { target: CondensedTarget; x: number; y: number };

/**
 * The condensed view: flat, laid out by ELK like the boxed one, with a tooltip where the boxes would
 * have said where a node lives. Every decision is condensed.ts's; this writes it out.
 */
function CondensedGraph({ payload, revealed, setRevealed }: Revealing) {
  // Null until ELK first answers, so that the layout counter below counts only real layouts: a probe
  // waiting for the condensed view to be drawn must not be satisfied by the empty first render.
  const [laid, setLaid] = useState<Positioned[] | null>(null);
  const [flow, setFlow] = useState<ReactFlowInstance | null>(null);
  const [hover, setHover] = useState<Hover | null>(null);
  const view = useMemo(() => condensed(payload, revealed), [payload, revealed]);

  useEffect(() => {
    let current = true;
    new ELK().layout(flatElkGraph(view, condensedCaption)).then((result) => {
      if (current) setLaid(positions(result));
    });
    return () => { current = false; };
  }, [view]);

  // The boxed view's refit and layout counter - see Graph.
  useEffect(() => {
    if (laid === null) return;
    flow?.fitView();
    const w = window as unknown as { drawn?: number };
    w.drawn = (w.drawn ?? 0) + 1;
  }, [flow, laid]);

  // A click changes what is under the pointer, so the tooltip it was showing may describe something
  // gone; it comes back on the next hover.
  const tapOn = useCallback((target: CondensedTarget) => {
    setHover(null);
    setRevealed((was) => tapCondensed(payload, was, target));
  }, [payload, setRevealed]);

  const edgeTarget = (edge: Edge): CondensedTarget =>
    ({ edge: [edge.source, edge.target, (edge.data as { kind: EdgeKind }).kind] });

  const nodes = useMemo(() => condensedNodesOf(view, laid ?? []), [view, laid]);
  const edges = useMemo(() => condensedEdgesOf(view), [view]);

  return (
    <>
      <ReactFlow nodes={nodes} edges={edges} nodeTypes={CONDENSED_TYPES}
                 onNodeClick={(_, node) => tapOn({ node: node.id })}
                 onEdgeClick={(_, edge) => tapOn(edgeTarget(edge))}
                 onNodeMouseEnter={(event, node) => setHover({ target: { node: node.id }, x: event.clientX, y: event.clientY })}
                 onNodeMouseLeave={() => setHover(null)}
                 onEdgeMouseEnter={(event, edge) => setHover({ target: edgeTarget(edge), x: event.clientX, y: event.clientY })}
                 onEdgeMouseLeave={() => setHover(null)}
                 onInit={setFlow} fitView minZoom={0.05} proOptions={{ hideAttribution: true }}>
        <Background />
      </ReactFlow>
      {hover && (
        <div className="cf-tooltip" style={{ left: hover.x + 12, top: hover.y + 12 }}>
          {hoverText(payload, revealed, hover.target).map((line, i) => <div key={i}>{line}</div>)}
        </div>
      )}
    </>
  );
}

type Mode = 'boxes' | 'condensed';

/**
 * The two views and the switch between them. Each view's `revealed` lives here rather than in the
 * view, so that switching away and back returns to what the reader had opened. Boxes is the default,
 * and the page it draws is the one it drew before there was a switch.
 */
function Page({ payload }: { payload: Payload }) {
  const [mode, setMode] = useState<Mode>('boxes');
  const [boxed, setBoxed] = useState<Set<Id>>(() => opening(payload));
  const [shown, setShown] = useState<Set<Id>>(() => condensedOpening());

  // Folding needs a box, so a sprawl inside the entry method has nothing to fold, and the condensed
  // view folds nothing at all. Without this the only way back is a reload, which re-runs the whole
  // layout.
  const reset = useCallback(() => {
    if (mode === 'boxes') setBoxed(opening(payload));
    else setShown(condensedOpening());
  }, [mode, payload]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'r' || event.key === 'R') reset();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [reset]);

  return (
    <>
      <div className="cf-toolbar">
        <button type="button" aria-pressed={mode === 'boxes'} onClick={() => setMode('boxes')}>Boxes</button>
        <button type="button" aria-pressed={mode === 'condensed'} onClick={() => setMode('condensed')}>Condensed</button>
        <button type="button" onClick={reset}>Reset</button>
      </div>
      {mode === 'boxes'
        ? <Graph payload={payload} revealed={boxed} setRevealed={setBoxed} />
        : <CondensedGraph payload={payload} revealed={shown} setRevealed={setShown} />}
    </>
  );
}

export function init(payload: Payload) {
  installStyles();
  createRoot(document.getElementById('graph')!).render(<Page payload={payload} />);
}
