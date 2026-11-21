/**
 * The renderer, and what --html emits.
 *
 * It derives nothing. Which nodes and lines are drawn, how each is captioned and which are faded are
 * fields on the condensed view, and this file writes them out. That is the whole boundary: a decision
 * made here is a decision no test can reach without a browser.
 *
 * Nothing here decides what is on screen. That is condensed.ts, where the tests are.
 */
import { useCallback, useEffect, useMemo, useRef, useState, type Dispatch, type ReactNode, type SetStateAction } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Background, BaseEdge, getBezierPath, Handle, MarkerType, Position, ReactFlow,
  type Edge, type EdgeProps, type Node, type NodeProps, type ReactFlowInstance, type Rect,
} from '@xyflow/react';
import css from '@xyflow/react/dist/style.css';
import ELK from 'elkjs/lib/elk.bundled.js';
import {
  boundaryChoices, boundaryDrawn, captionParts, choiceAt, choiceText, condensed, condensedCaption, condensedOpening, focusOf, focusPath, hoverText, PICK_AT, tapChoices,
  type Choice, type Focus, type FocusPath,
} from './condensed.ts';
import { condensedMermaid } from './mermaid.ts';
import {
  captionAt, curvedPath, flatEdgeId, flatElkGraph, positions, roundedPath, routes, FOCUS_LANE, NODE_HEIGHT,
  type LineStyle, type Point, type Positioned,
} from './layout.ts';
import {
  PALETTE, NODE_DEFAULT, NODE_OUTLINE, GROUP_FILL, BOUNDARY_OUTLINE, CLICKED_OUTLINE, GROUP_OUTLINE, NEW_RING, LABEL_BG, condensedEdgeStyle, legend,
  FOCUS_FILL, FOCUS_OUTLINE, DIM_OPACITY,
} from './theme.ts';
import {
  absolute, diffViews, easeOut, frame, HIGHLIGHT_MS, MOTION_CUTOFF, MOVE_MS, type Drawn,
} from './transition.ts';
import type {
  CondensedNode, CondensedTarget, CondensedView, Id, NodeType, Payload,
} from './types.ts';

/** The popup's width and tallest height, in pixels; where it opens is kept inside the window by these. */
const PICKER_WIDTH = 340;
const PICKER_HEIGHT = 400;

/** The stylesheet arrives as text so the page stays one file. */
function installStyles() {
  const style = document.createElement('style');
  style.textContent = `${css}
    .cf-node { border: 1px solid ${NODE_OUTLINE}; border-radius: 4px; font: 11px system-ui, sans-serif;
               display: flex; align-items: center; justify-content: center; box-sizing: border-box;
               width: 100%; height: 100%; }
    .cf-group { border: 2px solid ${GROUP_OUTLINE}; border-radius: 4px; background: ${GROUP_FILL};
                font: bold 11px system-ui, sans-serif; display: flex; align-items: center;
                justify-content: center; box-sizing: border-box; width: 100%; height: 100%; }
    .cf-toolbar { position: fixed; top: 8px; left: 8px; z-index: 10; display: flex; gap: 4px; }
    .cf-toolbar button { font: 12px system-ui, sans-serif; padding: 3px 10px; border: 1px solid #999;
                         border-radius: 4px; background: #fff; cursor: pointer; }
    .cf-toolbar button[aria-pressed="true"] { background: #333; color: #fff; }
    .cf-legend { position: fixed; top: 40px; left: 8px; z-index: 15; max-height: calc(100vh - 56px);
                 overflow-y: auto; background: #fff; border: 1px solid #999; border-radius: 4px;
                 padding: 6px 10px; font: 12px system-ui, sans-serif; box-shadow: 0 1px 4px #0003; }
    .cf-legend h4 { margin: 6px 0 4px; font-size: 12px; }
    .cf-legend-row { display: flex; align-items: center; gap: 8px; margin: 3px 0; }
    .cf-legend-row > :first-child { flex: none; width: 32px; height: 14px; box-sizing: border-box; border-radius: 3px; }
    .cf-legend-row b { font-weight: 600; }
    .cf-tooltip { position: fixed; z-index: 20; pointer-events: none; background: #fff;
                  border: 1px solid #999; border-radius: 4px; padding: 4px 8px;
                  font: 11px ui-monospace, monospace; white-space: pre; box-shadow: 0 1px 4px #0003; }
    .cf-picker { position: fixed; z-index: 25; width: ${PICKER_WIDTH}px; box-sizing: border-box; display: flex;
                 flex-direction: column; background: #fff; border: 1px solid #999; border-radius: 4px;
                 font: 12px system-ui, sans-serif; box-shadow: 0 2px 8px #0004; }
    .cf-picker-head { display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-bottom: 1px solid #ddd; }
    .cf-picker-head b { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .cf-picker-head button { border: 0; background: none; font-size: 16px; line-height: 1; cursor: pointer; }
    .cf-picker input[type="search"] { margin: 6px 8px 0; padding: 3px 6px; font: inherit; }
    .cf-picker-list { flex: 1; overflow-y: auto; padding: 0 8px 4px; }
    .cf-picker-list h4 { margin: 8px 0 2px; font-size: 11px; text-transform: uppercase; color: #666; }
    .cf-picker-row { display: flex; align-items: baseline; gap: 6px; padding: 1px 0; cursor: pointer; }
    .cf-picker-row input { flex: none; }
    .cf-picker-src { color: #888; font: 10px ui-monospace, monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; min-width: 0; }
    .cf-boundary-row { width: 100%; border: 0; background: none; text-align: left; font: inherit; }
    .cf-boundary-row:disabled { color: #888; cursor: default; }
    .cf-boundary-mark { flex: none; width: 12px; }
    .cf-picker-foot { display: flex; gap: 6px; justify-content: flex-end; padding: 6px 8px; border-top: 1px solid #ddd; }
    .cf-picker-foot button { font: 12px system-ui, sans-serif; padding: 3px 10px; border: 1px solid #999; border-radius: 4px;
                             background: #fff; cursor: pointer; }
    .cf-picker-foot button:disabled { opacity: 0.5; cursor: default; }
    .react-flow__handle { opacity: 0; }
    .react-flow__node.cf-new > * { animation: cf-ring ${HIGHLIGHT_MS}ms ease-out forwards; }
    .react-flow__edge.cf-new .react-flow__edge-path { animation: cf-edge-ring ${HIGHLIGHT_MS}ms ease-out forwards; }
    @keyframes cf-ring { from { box-shadow: 0 0 0 4px ${NEW_RING}; } to { box-shadow: 0 0 0 4px transparent; } }
    @keyframes cf-edge-ring { from { filter: drop-shadow(0 0 3px ${NEW_RING}); } to { filter: none; } }
    .react-flow__node.cf-clicked > * { outline: 2px solid ${CLICKED_OUTLINE}; outline-offset: 2px; }
    .cf-lane { background: ${FOCUS_FILL}; border: 2px solid ${FOCUS_OUTLINE}; border-radius: 8px;
               box-sizing: border-box; width: 100%; height: 100%; }
    .react-flow__node.cf-dim, .react-flow__edge.cf-dim { opacity: ${DIM_OPACITY}; }
    /* The lane is a region, not something a click revealed: no ring. */
    .react-flow__node-focusLane.cf-new > * { animation: none; box-shadow: none; }
    @media (prefers-reduced-motion: reduce) {
      .react-flow__node.cf-new > * { animation: none; box-shadow: 0 0 0 4px ${NEW_RING}; }
      .react-flow__edge.cf-new .react-flow__edge-path { animation: none; filter: drop-shadow(0 0 3px ${NEW_RING}); }
    }`;
  document.head.appendChild(style);
}

/**
 * A leaf, its `lead` - if the payload gave it one - in bold. The handles are invisible and exist
 * because React Flow will not draw an edge without them.
 */
function LeafNode({ data }: NodeProps) {
  const node = data as unknown as CondensedNode;
  const caption = captionParts(node);
  return (
    <div className="cf-node" style={{ background: PALETTE[node.type as NodeType] ?? NODE_DEFAULT }}>
      <Handle type="target" position={Position.Top} />
      {/* One span: as a flex item of its own, `rest` would lose its leading space - `callfetch`. */}
      <span>{caption.lead !== undefined && <strong>{caption.lead}</strong>}{caption.rest}</span>
      <Handle type="source" position={Position.Bottom} />
    </div>
  );
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
/** The route ELK computed for a condensed edge, which the fit must keep on screen as well as the nodes. */
const routeOf = (edge: Edge) => (edge.data as { route?: Point[] } | undefined)?.route;
const classes = (...names: (string | false)[]) => names.filter(Boolean).join(' ') || undefined;

/** The browser tests' layout counter - see `useMotion`. */
function countDrawn() {
  const w = window as unknown as { drawn?: number };
  w.drawn = (w.drawn ?? 0) + 1;
}

/**
 * From the drawing on screen to the next one, kept nodes glide, new ones grow out of
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
        // The drawing's own class - `cf-dim` - kept under the rings.
        ...n, className: classes(n.className ?? false, ring && fresh.has(n.id) && 'cf-new', n.id === outlined.current && 'cf-clicked'),
      })),
      edges: next.edges.map((e) => ({ ...e, className: classes(e.className ?? false, ring && freshEdges.has(e.id) && 'cf-new') })),
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
        // A route can run beside the nodes rather than between them, so it is fitted too.
        const bends = next.edges.flatMap((e) => routeOf(e) ?? []).map((p) => ({ ...p, width: 0, height: 0 }));
        const rects = [...next.nodes.map((n) => rectOf(n.id)), ...bends];
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

/** The region a click's path is laid out in. Nothing to say: the path inside it is the content. */
function LaneNode() {
  return <div className="cf-lane" />;
}

// Not `group`: React Flow has a built-in node type of that name.
const CONDENSED_TYPES = { leaf: LeafNode, condensedGroup: GroupNode, focusLane: LaneNode };

/** Off a focus's path: faded. With no focus nothing is. */
const dimmed = (path: FocusPath | null, on: boolean) => (path !== null && !on ? 'cf-dim' : undefined);

/** The lane first, if there is one: React Flow drops a child that arrives before its parent. */
function condensedNodesOf(view: CondensedView, laid: Positioned[], path: FocusPath | null): Node[] {
  const at = new Map(laid.map((p) => [p.id, p]));
  const lane = at.get(FOCUS_LANE);
  const nodes: Node[] = lane === undefined ? [] : [{
    id: FOCUS_LANE, type: 'focusLane', position: { x: lane.x, y: lane.y }, data: {},
    style: { width: lane.width, height: lane.height }, draggable: false, selectable: false,
  }];
  for (const node of view.nodes) {
    const p = at.get(node.id);
    if (p === undefined) continue;
    nodes.push({
      id: node.id,
      type: node.type === 'GROUP' ? 'condensedGroup' : 'leaf',
      position: { x: p.x, y: p.y },
      data: node as unknown as Record<string, unknown>,
      ...(p.parent ? { parentId: p.parent } : {}),
      style: { width: p.width, height: p.height || NODE_HEIGHT },
      className: dimmed(path, path?.nodes.has(node.id) ?? false),
      draggable: false,
    });
  }
  return nodes;
}

/** How far a routed line's corner is rounded off, in pixels. */
const CORNER = 6;

/**
 * A condensed line, drawn along the route ELK computed - which keeps clear of every node - rather than
 * from one fixed handle to another. Its `×N` sits on the middle of the route's longest stretch.
 *
 * Without a route it is the curve React Flow draws by default. That is only for the move: a route
 * laid out for where the nodes will be does not meet them while they glide, so the page takes the
 * routes away until the move ends (CondensedGraph). Every settled drawing has one for every line,
 * or condensedEdgesOf has already thrown.
 */
function RoutedEdge({
  id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, data,
  style, markerEnd, label, labelStyle, labelBgStyle, labelBgPadding,
}: EdgeProps) {
  const { route, lineStyle } = (data ?? {}) as { route?: Point[]; lineStyle?: LineStyle };
  let path: string;
  let at: Point;
  if (route) {
    path = lineStyle === 'curved' ? curvedPath(route) : roundedPath(route, CORNER);
    at = captionAt(route);
  } else {
    const [curve, x, y] = getBezierPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
    path = curve;
    at = { x, y };
  }
  return (
    <BaseEdge id={id} path={path} labelX={at.x} labelY={at.y} label={label} labelStyle={labelStyle}
              labelBgStyle={labelBgStyle} labelBgPadding={labelBgPadding} style={style} markerEnd={markerEnd} />
  );
}

const CONDENSED_EDGE_TYPES = { routed: RoutedEdge };

/**
 * One line per ordered pair, styled by condensedEdgeStyle; the caption on the page's white; drawn
 * along its route. A line ELK did not route is an error: drawn without one it would be a straight
 * guess through whatever lies between its ends.
 */
function condensedEdgesOf(view: CondensedView, routed: Map<string, Point[]>, path: FocusPath | null, lineStyle: LineStyle): Edge[] {
  return view.edges.map((edge) => {
    const { colour, label, dashed } = condensedEdgeStyle(edge);
    const id = flatEdgeId(edge);
    const route = routed.get(id);
    if (route === undefined) throw new Error(`ELK returned no route for edge ${id}`);
    return {
      id,
      type: 'routed',
      data: { route, lineStyle },
      source: edge.source,
      target: edge.target,
      className: dimmed(path, path?.edges.get(edge.source)?.has(edge.target) ?? false),
      ...(label === undefined ? {} : { label, labelBgStyle: { fill: LABEL_BG }, labelBgPadding: [4, 2] as [number, number] }),
      style: { stroke: colour, strokeWidth: 1.5, ...(dashed ? { strokeDasharray: '4 3' } : {}) },
      markerEnd: { type: MarkerType.ArrowClosed, color: colour },
    };
  });
}

type Hover = { target: CondensedTarget; x: number; y: number };

/** A click that could show more than PICK_AT things, waiting on the reader. `n` tells one popup from the next. */
type Pick = { n: number; target: CondensedTarget; title: string; choices: Choice[]; x: number; y: number };

/**
 * The frame both popups share: a title and `x`, a filter box past 10 rows, a scrolling list and an
 * optional footer. Where it sits is the caller's; `fit` keeps it inside the window.
 */
function Popup({ label, title, x, y, rows, onClose, children, foot }: {
  label: string; title: string; x: number; y: number; rows: number; onClose: () => void;
  children: (wanted: string) => ReactNode; foot?: ReactNode;
}) {
  const [query, setQuery] = useState('');
  const height = Math.min(PICKER_HEIGHT, window.innerHeight - 16);
  const left = Math.max(8, Math.min(x, window.innerWidth - PICKER_WIDTH - 8));
  const top = Math.max(8, Math.min(y, window.innerHeight - height - 8));
  return (
    <div className="cf-picker" role="dialog" aria-label={label} style={{ left, top, maxHeight: height }}>
      <div className="cf-picker-head">
        <b>{title}</b>
        <button type="button" aria-label="Close" onClick={onClose}>×</button>
      </div>
      {rows > 10 && (
        <input type="search" placeholder="Filter" aria-label="Filter" value={query} onChange={(e) => setQuery(e.target.value)} />
      )}
      <div className="cf-picker-list">{children(query.trim().toLowerCase())}</div>
      {foot}
    </div>
  );
}

/**
 * The popup a long click opens: what the click could show, by section, to tick. Rows are filtered by
 * label (search box past 10 rows); `Show selected` and `Show all` both close it, as do `x` and the
 * page's Escape and pane click.
 */
function Picker({ payload, pick, onShow, onClose }: {
  payload: Payload; pick: Pick; onShow: (ids: Id[]) => void; onClose: () => void;
}) {
  const [ticked, setTicked] = useState<Set<Id>>(new Set());
  const { choices } = pick;
  const tick = (id: Id) => setTicked((was) => {
    const next = new Set(was);
    if (!next.delete(id)) next.add(id);
    return next;
  });
  const foot = (
    <div className="cf-picker-foot">
      <button type="button" disabled={ticked.size === 0}
              onClick={() => onShow(choices.filter((c) => ticked.has(c.id)).flatMap((c) => c.reveal))}>
        Show selected ({ticked.size})
      </button>
      <button type="button" onClick={() => onShow(choices.flatMap((c) => c.reveal))}>Show all</button>
    </div>
  );
  return (
    <Popup label="Choose what to show" title={pick.title} x={pick.x} y={pick.y} rows={choices.length} onClose={onClose} foot={foot}>
      {(wanted) => {
        const rows = choices.filter((c) => c.label.toLowerCase().includes(wanted));
        return [...new Set(rows.map((c) => c.section))].map((section) => (
          <div key={section}>
            <h4>{section}</h4>
            {rows.filter((c) => c.section === section).map((c) => {
              const text = choiceText(payload, c);
              return (
                <label key={c.id} className="cf-picker-row">
                  <input type="checkbox" aria-label={c.label} checked={ticked.has(c.id)} onChange={() => tick(c.id)} />
                  <span>{c.label}</span>
                  {text !== null && <span className="cf-picker-src" title={text}>{text}</span>}
                </label>
              );
            })}
          </div>
        ));
      }}
    </Popup>
  );
}

/**
 * The toolbar's Boundaries list: every boundary call site by class, one click adding that box. A
 * row already on screen is muted with a check mark and does nothing; the panel stays open, so the
 * reader can add several while watching the graph.
 */
function Boundaries({ payload, revealed, onAdd, onClose }: {
  payload: Payload; revealed: Set<Id>; onAdd: (id: Id) => void; onClose: () => void;
}) {
  const choices = useMemo(() => boundaryChoices(payload, revealed), [payload, revealed]);
  return (
    <Popup label="Boundaries" title={`Boundaries (${choices.length})`} x={8} y={40} rows={choices.length} onClose={onClose}>
      {(wanted) => {
        const rows = choices.filter((c) => c.label.toLowerCase().includes(wanted) || c.section.toLowerCase().includes(wanted));
        return [...new Set(rows.map((c) => c.section))].map((section) => (
          <div key={section}>
            <h4>{section}</h4>
            {rows.filter((c) => c.section === section).map((c) => {
              const drawn = boundaryDrawn(payload, revealed, c);
              const text = choiceText(payload, c);
              const at = choiceAt(c);
              return (
                <button key={c.id} type="button" className="cf-picker-row cf-boundary-row" disabled={drawn}
                        aria-label={drawn ? `${c.label} (on screen)` : c.label} onClick={() => onAdd(c.id)}>
                  <span className="cf-boundary-mark">{drawn ? '✓' : ''}</span>
                  <span>{c.label}</span>
                  {text !== null && <span className="cf-picker-src" title={text}>{text}</span>}
                  {at !== null && <span className="cf-picker-src">{at}</span>}
                </button>
              );
            })}
          </div>
        ));
      }}
    </Popup>
  );
}

/**
 * The view: flat, laid out by ELK, with a tooltip saying where a node lives. Every decision is
 * condensed.ts's; this writes it out.
 */
function CondensedGraph({ payload, revealed, setRevealed, view, focus, setFocus, lineStyle, boundaries, closeBoundaries }: Revealing & {
  lineStyle: LineStyle; boundaries: boolean; closeBoundaries: () => void; view: CondensedView; focus: Focus | null; setFocus: Dispatch<SetStateAction<Focus | null>>;
}) {
  // Null until ELK first answers, so that the layout counter counts only real layouts: a probe
  // waiting for the condensed view to be drawn must not be satisfied by the empty first render.
  const [target, setTarget] = useState<Target | null>(null);
  const [flow, setFlow] = useState<ReactFlowInstance | null>(null);
  const [hover, setHover] = useState<Hover | null>(null);
  const [pick, setPick] = useState<Pick | null>(null);
  const picks = useRef(0);
  const previous = useRef<Map<Id, Point>>(undefined);
  const click = useRef<Click | undefined>(undefined);
  const path = useMemo(() => (focus === null ? null : focusPath(payload, revealed, focus)), [payload, revealed, focus]);

  useEffect(() => {
    let current = true;
    new ELK().layout(flatElkGraph(view, condensedCaption, previous.current, path?.nodes, lineStyle)).then((result) => {
      if (!current) return;
      const laid = positions(result);
      previous.current = new Map(laid.map((p) => [p.id, { x: p.x, y: p.y }]));
      const drawing = { nodes: condensedNodesOf(view, laid, path), edges: condensedEdgesOf(view, routes(result), path, lineStyle) };
      setTarget({ drawing, click: click.current });
      click.current = undefined;
    });
    return () => { current = false; };
  }, [view, path, lineStyle]);

  // Motion, refit and the layout counter - see useMotion.
  const { drawing, moving } = useMotion(flow, target);
  // While the nodes glide, each line follows its two ends (RoutedEdge without a route); the routes,
  // laid out for where the nodes are going, come back when the move ends.
  const edges = useMemo(
    () => (moving ? drawing.edges.map((edge) => ({ ...edge, data: { ...edge.data, route: undefined } })) : drawing.edges),
    [drawing, moving],
  );

  // A click changes what is under the pointer, so the tooltip it was showing may describe something
  // gone; it comes back on the next hover.
  const show = useCallback((on: CondensedTarget, ids: Id[]) => {
    setHover(null);
    setPick(null);
    click.current = 'node' in on ? { id: on.node } : {};
    setFocus(focusOf(payload, revealed, on));
    setRevealed((was) => new Set([...was, ...ids]));
  }, [payload, revealed, setRevealed, setFocus]);

  // A click that could show more than PICK_AT things asks which; otherwise it shows them all.
  const tapOn = useCallback((on: CondensedTarget, at: { clientX: number; clientY: number }) => {
    const choices = tapChoices(payload, revealed, on);
    if (choices.length <= PICK_AT) return show(on, choices.flatMap((c) => c.reveal));
    const caption = (id: Id) => {
      const node = view.nodes.find((n) => n.id === id);
      return node === undefined ? id : condensedCaption(node);
    };
    setHover(null);
    picks.current += 1;
    setPick({
      n: picks.current, target: on, choices, x: at.clientX, y: at.clientY,
      title: 'node' in on ? caption(on.node) : `${caption(on.edge[0])} → ${caption(on.edge[1])}`,
    });
  }, [payload, revealed, view, show]);

  // Back to the whole graph, where it is: no click to anchor on, and nothing new to refit for.
  const unfocus = useCallback(() => {
    setPick(null);
    if (focus === null) return;
    click.current = {};
    setFocus(null);
  }, [focus, setFocus]);
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      unfocus();
      closeBoundaries();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [unfocus, closeBoundaries]);

  const edgeTarget = (edge: Edge): CondensedTarget => ({ edge: [edge.source, edge.target] });

  return (
    <>
      <ReactFlow nodes={drawing.nodes} edges={edges} nodeTypes={CONDENSED_TYPES} edgeTypes={CONDENSED_EDGE_TYPES}
                 className={moving ? 'cf-moving' : undefined}
                 onNodeClick={(event, node) => (node.id === FOCUS_LANE ? unfocus() : tapOn({ node: node.id }, event))}
                 onEdgeClick={(event, edge) => tapOn(edgeTarget(edge), event)}
                 onPaneClick={unfocus}
                 onNodeMouseEnter={(event, node) => {
                   if (node.id !== FOCUS_LANE) setHover({ target: { node: node.id }, x: event.clientX, y: event.clientY });
                 }}
                 onNodeMouseLeave={() => setHover(null)}
                 onEdgeMouseEnter={(event, edge) => setHover({ target: edgeTarget(edge), x: event.clientX, y: event.clientY })}
                 onEdgeMouseLeave={() => setHover(null)}
                 onInit={setFlow} minZoom={0.05} proOptions={{ hideAttribution: true }}>
        <Background />
      </ReactFlow>
      {pick && <Picker key={pick.n} payload={payload} pick={pick} onShow={(ids) => show(pick.target, ids)} onClose={() => setPick(null)} />}
      {boundaries && <Boundaries payload={payload} revealed={revealed} onAdd={(id) => show({ node: id }, [id])} onClose={closeBoundaries} />}
      {hover && (
        <div className="cf-tooltip" style={{ left: hover.x + 12, top: hover.y + 12 }}>
          {hoverText(payload, revealed, hover.target).map((line, i) => <div key={i}>{line}</div>)}
        </div>
      )}
    </>
  );
}

/** The condensed view as a Mermaid document, fenced so it renders where it is opened, saved by the browser. */
function download(view: CondensedView) {
  const blob = new Blob(['```mermaid\n', condensedMermaid(view), '```\n'], { type: 'text/markdown' });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = 'codeflow-condensed.md';
  link.click();
  URL.revokeObjectURL(link.href);
}

/** What each colour, outline and line on the page means - rows from theme.ts's `legend`. */
function Legend({ payload }: { payload: Payload }) {
  const { leaves, boxes, lines } = legend(payload);
  const swatch = (r: { name: string; fill: string; outline: string; meaning: string }, width: number) => (
    <div key={r.name} className="cf-legend-row">
      <div style={{ background: r.fill, border: `${width}px solid ${r.outline}` }} />
      <span><b>{r.name}</b> {r.meaning}</span>
    </div>
  );
  return (
    <div className="cf-legend" role="dialog" aria-label="Legend">
      <h4>Values</h4>
      {leaves.map((r) => swatch(r, 1))}
      <h4>Method boxes</h4>
      {boxes.map((r) => swatch(r, 2))}
      <h4>Lines</h4>
      {lines.map((r) => (
        <div key={r.name} className="cf-legend-row">
          <svg width="32" height="14"><line x1="1" y1="7" x2="31" y2="7" stroke={r.colour} strokeWidth="2"
            strokeDasharray={r.dashed ? '4 3' : undefined} /></svg>
          <span><b>{r.name.toLowerCase()}</b> {r.meaning}</span>
        </div>
      ))}
    </div>
  );
}

/**
 * The page: the toolbar and the one view. `shown` and `focus` live here rather than in the view so
 * that Reset and the Mermaid button reach them.
 */
function Page({ payload }: { payload: Payload }) {
  const [legendOpen, setLegendOpen] = useState(false);
  const [boundariesOpen, setBoundariesOpen] = useState(false);
  const closeBoundaries = useCallback(() => setBoundariesOpen(false), []);
  const hasBoundaries = useMemo(() => boundaryChoices(payload, new Set()).length > 0, [payload]);
  // Square or curved lines; changing it re-lays-out the same view, so `shown` and `focus` stay.
  const [lineStyle, setLineStyle] = useState<LineStyle>('square');
  const [shown, setShown] = useState<Set<Id>>(() => condensedOpening(payload));
  // The click's path - see focusPath. Here beside `shown` so that Reset clears both.
  const [focus, setFocus] = useState<Focus | null>(null);
  // Here rather than in CondensedGraph so that the Mermaid button exports the very view drawn.
  const condensedView = useMemo(() => condensed(payload, shown), [payload, shown]);

  // The condensed view folds nothing, so without this the only way back is a reload, which re-runs
  // the whole layout.
  const reset = useCallback(() => {
    setShown(condensedOpening(payload));
    setFocus(null);
  }, [payload]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      // Not while typing in the popup's filter.
      if ((event.key === 'r' || event.key === 'R') && !(event.target instanceof HTMLInputElement)) reset();
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [reset]);

  return (
    <>
      <div className="cf-toolbar">
        <button type="button" onClick={reset}>Reset</button>
        <button type="button" onClick={() => download(condensedView)}>Mermaid</button>
        <button type="button" aria-pressed={legendOpen} onClick={() => setLegendOpen((open) => !open)}>Legend</button>
        <button type="button" aria-pressed={boundariesOpen} disabled={!hasBoundaries}
                onClick={() => setBoundariesOpen((open) => !open)}>Boundaries</button>
        <button type="button" aria-pressed={lineStyle === 'curved'}
                onClick={() => setLineStyle((style) => (style === 'curved' ? 'square' : 'curved'))}>Curved</button>
      </div>
      {legendOpen && <Legend payload={payload} />}
      <CondensedGraph payload={payload} revealed={shown} setRevealed={setShown} view={condensedView}
                      focus={focus} setFocus={setFocus} lineStyle={lineStyle}
                      boundaries={boundariesOpen} closeBoundaries={closeBoundaries} />
    </>
  );
}

export function init(payload: Payload) {
  installStyles();
  createRoot(document.getElementById('graph')!).render(<Page payload={payload} />);
}
