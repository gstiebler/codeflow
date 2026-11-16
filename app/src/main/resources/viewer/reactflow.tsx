/**
 * The renderer, and what --html emits.
 *
 * It derives nothing. Whether an edge is drawn, whether a box is drawn, which leaves are showing and
 * what each one is captioned are all fields on the view, and this file writes them out. That is the
 * whole boundary: a decision made here is a decision no test can reach without a browser.
 *
 * Nothing here decides what is on screen. That is model.ts, where the tests are.
 */
import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Background, Handle, MarkerType, Position, ReactFlow,
  type Edge, type Node, type NodeProps, type ReactFlowInstance, type Rect,
} from '@xyflow/react';
import css from '@xyflow/react/dist/style.css';
import ELK from 'elkjs/lib/elk.bundled.js';
import { captionParts, opening, screen, tap } from './model.ts';
import { condensed, condensedCaption, condensedOpening, hoverText, tapCondensed } from './condensed.ts';
import {
  elkGraph, flatEdgeId, flatElkGraph, positions, NODE_HEIGHT, TITLE_HEIGHT, type Point, type Positioned,
} from './layout.ts';
import {
  PALETTE, NODE_DEFAULT, BOUNDARY_OUTLINE, CLICKED_OUTLINE, GROUP_OUTLINE, NEW_RING, LABEL_BG, condensedEdgeStyle, edgeStyle,
} from './theme.ts';
import {
  absolute, diffViews, easeOut, frame, HIGHLIGHT_MS, MOTION_CUTOFF, MOVE_MS, type Drawn,
} from './transition.ts';
import type {
  CondensedNode, CondensedTarget, CondensedView, Id, Payload, View, ViewNode,
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
    .react-flow__handle { opacity: 0; }
    /* Boxes answers no click on an edge, and an edge drawn over a box's title - an entry point's event
       into a closed callee's stub - would otherwise take the click the title is there for. */
    .cf-boxes .react-flow__edge { pointer-events: none; }
    .react-flow__node.cf-new > * { animation: cf-ring ${HIGHLIGHT_MS}ms ease-out forwards; }
    .react-flow__edge.cf-new .react-flow__edge-path { animation: cf-edge-ring ${HIGHLIGHT_MS}ms ease-out forwards; }
    @keyframes cf-ring { from { box-shadow: 0 0 0 4px ${NEW_RING}; } to { box-shadow: 0 0 0 4px transparent; } }
    @keyframes cf-edge-ring { from { filter: drop-shadow(0 0 3px ${NEW_RING}); } to { filter: none; } }
    .react-flow__node.cf-clicked > * { outline: 2px solid ${CLICKED_OUTLINE}; outline-offset: 2px; }
    @media (prefers-reduced-motion: reduce) {
      .react-flow__node.cf-new > * { animation: none; box-shadow: 0 0 0 4px ${NEW_RING}; }
      .react-flow__edge.cf-new .react-flow__edge-path { animation: none; filter: drop-shadow(0 0 3px ${NEW_RING}); }
    }`;
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
        // No `extent: 'parent'`: nothing is draggable, and React Flow clamps a child to its parent's
        // *measured* size, which a box that has just been resized does not have yet - every frame of
        // a move would put its children in its corner.
        ...(node.parent ? { parentId: node.parent } : {}),
        style: { width: p.width, height: p.height || NODE_HEIGHT },
        draggable: false,
      };
    });
}

/** A drawing as React Flow takes it: what a layout landed on, or a frame on the way there. */
type Drawing = { nodes: Node[]; edges: Edge[] };

/**
 * What the click a drawing answers was on: a node, or `{}` for an edge, which has no place for the
 * new nodes to grow out of. No click at all (the reset key) is `undefined`.
 */
type Click = { id?: Id };

/** A layout that has landed, and the click that asked for it. */
type Target = { drawing: Drawing; click?: Click };

const placed = (nodes: Node[]): Drawn =>
  new Map(nodes.map((n) => [n.id, { x: n.position.x, y: n.position.y, parent: n.parentId }]));
const size = (n: Node) => ({ width: n.style!.width as number, height: n.style!.height as number });
const lerp = (a: number, b: number, e: number) => a + (b - a) * e;
const classes = (...names: (string | false)[]) => names.filter(Boolean).join(' ') || undefined;

/** The browser tests' layout counter - see `useMotion`. */
function countDrawn() {
  const w = window as unknown as { drawn?: number };
  w.drawn = (w.drawn ?? 0) + 1;
}

/**
 * From the drawing on screen to the next one, for both views: kept nodes glide, new ones grow out of
 * the clicked node and fade in, gone ones fade out where they were, and the viewport holds the clicked
 * node still on screen. transition.ts decides where each node is at each moment; this paints it.
 *
 * Then it refits, but only if something new landed off screen or no click asked for the drawing
 * (Reset) - refitting after every click is what made each one feel like a different graph - and only
 * then counts the drawing: `window.drawn` is for
 * the browser tests, which have no other way to know a click has finished. It counts *layouts*, not
 * renders or frames, on purpose - a probe that waited for the page to agree with its own `view` would
 * be asking the renderer whether the renderer is right. A transition cut short by the next layout is
 * not counted, and the next one starts from wherever it left the nodes. Nothing in the page reads it.
 *
 * The first drawing, reduced motion, a zero duration (`window.cfAnimationMs`, for tests) and a next
 * drawing past MOTION_CUTOFF all jump straight to the end; the rings are kept.
 */
function useMotion(flow: ReactFlowInstance | null, target: Target | null) {
  const [drawing, setDrawing] = useState<Drawing>({ nodes: [], edges: [] });
  const [moving, setMoving] = useState(false);
  const shown = useRef<Drawing | null>(null);
  const fading = useRef<Set<Id>>(new Set());
  const outlined = useRef<Id | undefined>(undefined);
  const committed = useRef<(() => void) | undefined>(undefined);

  // `then` runs once the drawing is on the page, not merely asked for.
  const paint = (next: Drawing, then?: () => void) => {
    shown.current = next;
    committed.current = then;
    setDrawing(next);
  };
  useEffect(() => {
    const then = committed.current;
    committed.current = undefined;
    then?.();
  }, [drawing]);

  useEffect(() => {
    if (flow === null || target === null) return;
    let cancelled = false;
    let raf = 0;
    let timer = 0;
    // A move cut short leaves its fading-out nodes on screen; they are gone already, and starting
    // from them would draw them whole again and fade them a second time.
    const prev = shown.current && { ...shown.current, nodes: shown.current.nodes.filter((n) => !fading.current.has(n.id)) };
    fading.current = new Set();
    const next = target.drawing;
    if (target.click) outlined.current = target.click.id;
    const clicked = target.click?.id;

    const before: Drawn = prev ? placed(prev.nodes) : new Map();
    const after = placed(next.nodes);
    const diff = diffViews(before, after, clicked);
    const nextById = new Map(next.nodes.map((n) => [n.id, n]));
    const fresh = new Set(prev ? diff.added : []);
    const oldEdges = new Set(prev?.edges.map((e) => e.id));
    const freshEdges = new Set(prev ? next.edges.filter((e) => !oldEdges.has(e.id)).map((e) => e.id) : []);
    const ringed = (ring: boolean): Drawing => ({
      nodes: next.nodes.map((n) => ({
        ...n, className: classes(ring && fresh.has(n.id) && 'cf-new', n.id === outlined.current && 'cf-clicked'),
      })),
      edges: next.edges.map((e) => ({ ...e, className: classes(ring && freshEdges.has(e.id) && 'cf-new') })),
    });
    const settled = ringed(true);

    // The clicked node kept where it is on screen: the viewport moves against it.
    const v0 = flow.getViewport();
    const from = clicked !== undefined && before.has(clicked) && after.has(clicked) ? absolute(before, clicked) : undefined;
    const anchor = (at: Drawn) => {
      if (from === undefined) return;
      const p = absolute(at, clicked!);
      void flow.setViewport({ x: v0.x - (p.x - from.x) * v0.zoom, y: v0.y - (p.y - from.y) * v0.zoom, zoom: v0.zoom });
    };

    const rectOf = (id: Id): Rect => ({ ...absolute(after, id), ...size(nextById.get(id)!) });
    const offScreen = () => {
      const box = document.querySelector('.react-flow')!.getBoundingClientRect();
      return diff.added.some((id) => {
        const r = rectOf(id);
        const a = flow.flowToScreenPosition({ x: r.x, y: r.y });
        const b = flow.flowToScreenPosition({ x: r.x + r.width, y: r.y + r.height });
        return a.x < box.left || a.y < box.top || b.x > box.right || b.y > box.bottom;
      });
    };
    const land = async (duration: number) => {
      // No click (Reset): nothing anchors the reader, and what is left may be anywhere - fit it all.
      if (prev === null || target.click === undefined || offScreen()) {
        const rects = next.nodes.map((n) => rectOf(n.id));
        const x = Math.min(...rects.map((r) => r.x));
        const y = Math.min(...rects.map((r) => r.y));
        const width = Math.max(...rects.map((r) => r.x + r.width)) - x;
        const height = Math.max(...rects.map((r) => r.y + r.height)) - y;
        await flow.fitBounds({ x, y, width, height }, { duration });
      }
      if (cancelled) return;
      countDrawn();
      timer = window.setTimeout(() => paint(ringed(false)), HIGHLIGHT_MS);
    };

    const ms = (window as unknown as { cfAnimationMs?: number }).cfAnimationMs ?? MOVE_MS;
    const still = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prev === null || still || ms <= 0 || next.nodes.length > MOTION_CUTOFF) {
      anchor(after);
      paint(settled, () => void land(0));
    } else {
      const prevById = new Map(prev.nodes.map((n) => [n.id, n]));
      const going = prev.nodes.filter((n) => !after.has(n.id)).map((n) => ({ ...n, className: undefined }));
      fading.current = new Set(going.map((n) => n.id));
      const t0 = performance.now();
      setMoving(true);
      const step = (now: number) => {
        if (cancelled) return;
        const t = Math.min(1, (now - t0) / ms);
        const e = easeOut(t);
        const at = frame(diff, after, t);
        const nodes = settled.nodes.map((n) => {
          const was = prevById.get(n.id);
          const style = was
            ? { ...n.style, width: lerp(size(was).width, size(n).width, e), height: lerp(size(was).height, size(n).height, e) }
            : { ...n.style, opacity: e };
          return { ...n, position: at.get(n.id)!, style };
        });
        paint({
          nodes: [...nodes, ...going.map((n) => ({ ...n, style: { ...n.style, opacity: 1 - e } }))],
          edges: settled.edges.map((edge) => (freshEdges.has(edge.id) ? { ...edge, style: { ...edge.style, opacity: e } } : edge)),
        });
        anchor(at);
        if (t < 1) raf = requestAnimationFrame(step);
        else {
          setMoving(false);
          fading.current = new Set();
          paint(settled, () => void land(ms));
        }
      };
      raf = requestAnimationFrame(step);
    }
    return () => {
      cancelled = true;
      cancelAnimationFrame(raf);
      clearTimeout(timer);
      setMoving(false);
    };
  }, [flow, target]);

  return { drawing, moving };
}

/** What a view is handed by the page: the payload and its own `revealed`, which the page keeps. */
type Revealing = { payload: Payload; revealed: Set<Id>; setRevealed: Dispatch<SetStateAction<Set<Id>>> };

function Graph({ payload, revealed, setRevealed }: Revealing) {
  const [target, setTarget] = useState<Target | null>(null);
  const [flow, setFlow] = useState<ReactFlowInstance | null>(null);
  const view = useMemo(() => screen(payload, revealed), [payload, revealed]);
  const previous = useRef<Map<Id, Point>>(undefined);
  const click = useRef<Click | undefined>(undefined);

  useEffect(() => {
    let current = true;
    // No cast either way: layout.ts's ElkNode is assignable to the library's, and what comes back is
    // assignable to positions(). Keeping the two types structurally compatible is what lets layout.ts
    // stay importable without elkjs.
    //
    // Seeded from the last drawing, so that what was on screen stays roughly where it was - see
    // `stabilise`. A view switch remounts this, so a switch lays out from scratch.
    new ELK().layout(elkGraph(view, previous.current)).then((result) => {
      if (!current) return;
      const laid = positions(result);
      previous.current = new Map(laid.map((p) => [p.id, { x: p.x, y: p.y }]));
      setTarget({ drawing: { nodes: nodesOf(view, laid), edges: edgesOf(view) }, click: click.current });
      click.current = undefined;
    });
    return () => { current = false; };
  }, [view]);

  // The old drawing stays on screen until the new one has a layout, then moves into it. A reveal
  // the reader cannot see is the one thing progressive reveal cannot afford, so it refits when
  // something new would land off screen.
  const { drawing, moving } = useMotion(flow, target);

  const onNodeClick = useCallback((_: unknown, node: Node) => {
    click.current = { id: node.id };
    setRevealed((was) => tap(payload, was, node.id));
  }, [payload, setRevealed]);

  // Something to inspect from the console when a page looks wrong. The browser tests deliberately do
  // not read it: a page whose view is right and whose drawing is not is exactly what they are for.
  (window as unknown as { view: View }).view = view;

  return (
    <ReactFlow nodes={drawing.nodes} edges={drawing.edges} nodeTypes={NODE_TYPES} onNodeClick={onNodeClick}
               className={moving ? 'cf-boxes cf-moving' : 'cf-boxes'}
               onInit={setFlow} minZoom={0.05} proOptions={{ hideAttribution: true }}>
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

/** One line per ordered pair, styled by condensedEdgeStyle; the caption on the page's white. */
function condensedEdgesOf(view: CondensedView): Edge[] {
  return view.edges.map((edge) => {
    const { colour, label, dashed } = condensedEdgeStyle(edge);
    return {
      id: flatEdgeId(edge),
      source: edge.source,
      target: edge.target,
      ...(label === undefined ? {} : { label, labelBgStyle: { fill: LABEL_BG }, labelBgPadding: [4, 2] as [number, number] }),
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
  // Null until ELK first answers, so that the layout counter counts only real layouts: a probe
  // waiting for the condensed view to be drawn must not be satisfied by the empty first render.
  const [target, setTarget] = useState<Target | null>(null);
  const [flow, setFlow] = useState<ReactFlowInstance | null>(null);
  const [hover, setHover] = useState<Hover | null>(null);
  const view = useMemo(() => condensed(payload, revealed), [payload, revealed]);
  const previous = useRef<Map<Id, Point>>(undefined);
  const click = useRef<Click | undefined>(undefined);

  useEffect(() => {
    let current = true;
    new ELK().layout(flatElkGraph(view, condensedCaption, previous.current)).then((result) => {
      if (!current) return;
      const laid = positions(result);
      previous.current = new Map(laid.map((p) => [p.id, { x: p.x, y: p.y }]));
      setTarget({ drawing: { nodes: condensedNodesOf(view, laid), edges: condensedEdgesOf(view) }, click: click.current });
      click.current = undefined;
    });
    return () => { current = false; };
  }, [view]);

  // The boxed view's motion, refit and layout counter - see useMotion.
  const { drawing, moving } = useMotion(flow, target);

  // A click changes what is under the pointer, so the tooltip it was showing may describe something
  // gone; it comes back on the next hover.
  const tapOn = useCallback((on: CondensedTarget) => {
    setHover(null);
    click.current = 'node' in on ? { id: on.node } : {};
    setRevealed((was) => tapCondensed(payload, was, on));
  }, [payload, setRevealed]);

  const edgeTarget = (edge: Edge): CondensedTarget => ({ edge: [edge.source, edge.target] });

  return (
    <>
      <ReactFlow nodes={drawing.nodes} edges={drawing.edges} nodeTypes={CONDENSED_TYPES}
                 className={moving ? 'cf-moving' : undefined}
                 onNodeClick={(_, node) => tapOn({ node: node.id })}
                 onEdgeClick={(_, edge) => tapOn(edgeTarget(edge))}
                 onNodeMouseEnter={(event, node) => setHover({ target: { node: node.id }, x: event.clientX, y: event.clientY })}
                 onNodeMouseLeave={() => setHover(null)}
                 onEdgeMouseEnter={(event, edge) => setHover({ target: edgeTarget(edge), x: event.clientX, y: event.clientY })}
                 onEdgeMouseLeave={() => setHover(null)}
                 onInit={setFlow} minZoom={0.05} proOptions={{ hideAttribution: true }}>
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
  const [shown, setShown] = useState<Set<Id>>(() => condensedOpening(payload));

  // Folding needs a box, so a sprawl inside the entry method has nothing to fold, and the condensed
  // view folds nothing at all. Without this the only way back is a reload, which re-runs the whole
  // layout.
  const reset = useCallback(() => {
    if (mode === 'boxes') setBoxed(opening(payload));
    else setShown(condensedOpening(payload));
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
