/**
 * High-level projections of a codeflow payload: Architecture (Level 1) and Workflow (Level 2),
 * plus Expression Contraction (Item 1), Summary Cards (Item 2), Semantic Roles (Item 3),
 * Domain Entity State Cards (Item 4), and Assumed DI badges (Item 6).
 *
 * Same purity contract as condensed.ts: zero DOM, zero React/ELK imports, pure functions of
 * `(payload, options)` returning a `CondensedView` that layout.ts, mermaid.ts and reactflow.tsx
 * lay out, route, animate and export without special cases.
 */
import { shortMethod } from './condensed.ts';
import type {
  CondensedEdge, CondensedFrame, CondensedNode, CondensedTarget, CondensedView,
  EdgeKind, Id, MethodSummary, Payload, PayloadEdge, PayloadNode,
} from './types.ts';

/** Max items shown per summary line before `+N`. */
const SUMMARY_CAP = 3;

/** Max length of a contracted decision or expression caption. */
const CAPTION_MAX = 36;

/** Compact multi-line effect summary rendered on Summary Cards and hover tooltips. */
export function summaryLines(summary: MethodSummary | undefined, assumed?: boolean): string[] {
  const lines: string[] = [];
  const fmt = (prefix: string, items: string[]) => {
    if (items.length === 0) return;
    const shown = items.slice(0, SUMMARY_CAP).join(', ');
    lines.push(`${prefix}: ${shown}${items.length > SUMMARY_CAP ? ` +${items.length - SUMMARY_CAP}` : ''}`);
  };
  if (summary) {
    fmt('writes', summary.writes);
    fmt('reads', summary.reads);
    fmt('calls', summary.boundaries);
    fmt('throws', summary.throws);
    if (summary.gaps > 0) lines.push(`${summary.gaps} ${summary.gaps === 1 ? 'gap' : 'gaps'}`);
  }
  if (assumed) lines.push('assumed: single impl');
  return lines;
}

function where(source: string | undefined): { file: string; name: string; line: string } | null {
  const match = source === undefined ? null : /^(.*):(\d+):(\d+)$/.exec(source);
  if (!match) return null;
  const file = match[1];
  return { file, name: file.slice(Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\')) + 1), line: match[2] };
}

function sourceLine(payload: Payload, source: string | undefined): string | null {
  const at = where(source);
  if (!at) return null;
  return payload.lines?.[at.file]?.[at.line] ?? null;
}

function clip(text: string, max = CAPTION_MAX): string {
  const clean = text.replace(/\s+/g, ' ').trim();
  return clean.length <= max ? clean : `${clean.slice(0, max - 1)}…`;
}

/**
 * Extracts a concise predicate from a decision's source line (`if (amount > balance)` -> `if: amount > balance`),
 * falling back to the node's own label when no line is available.
 */
export function decisionCaption(payload: Payload, node: PayloadNode): string {
  const line = sourceLine(payload, node.source);
  if (line !== null) {
    const ifMatch = /\bif\s*\((.+)\)/.exec(line);
    if (ifMatch) return clip(`if: ${ifMatch[1].replace(/\)\s*\{?.*$/, '').trim()}`);
    const swMatch = /\bswitch\s*\((.+?)\)/.exec(line);
    if (swMatch) return clip(`switch: ${swMatch[1].trim()}`);
    const ternMatch = /([^=]+?)\s*\?/.exec(line);
    if (ternMatch && node.label === 'ternary') return clip(`?: ${ternMatch[1].trim()}`);
  }
  return node.label;
}

/** Ids of `BIN_OP` nodes that act as gated decisions (receiving at least one `CONDITION` edge). */
export function decisionIds(payload: Payload): Set<Id> {
  const byId = new Map(payload.nodes.map((n) => [n.id, n]));
  const ids = new Set<Id>();
  for (const edge of payload.edges) {
    if (edge.kind === 'CONDITION' && byId.get(edge.target)?.type === 'BIN_OP') {
      ids.add(edge.target);
    }
  }
  return ids;
}

/**
 * Whether a leaf node is a high-level anchor kept in Expression Contraction (Item 1).
 *
 * Non-anchors (`LITERAL`, pure `BIN_OP`, local `VARIABLE`/`OBJ_VARIABLE` without `fieldOwner`,
 * and `NOISE` calls when `hideNoise` is true) are contracted into derived edges between anchors.
 * `UNMODELLED` is ALWAYS an anchor so gaps are never silently hidden.
 */
export function isAnchor(
  node: PayloadNode,
  decisions: Set<Id>,
  hideNoise: boolean,
  expanded?: Set<Id>,
): boolean {
  if (node.type === 'METHOD') return false;
  if (expanded?.has(node.id)) return true;
  switch (node.type) {
    case 'EVENT':
    case 'FUNC_PARAM':
    case 'RETURN':
    case 'THROW':
    case 'BOUNDARIES':
    case 'UNMODELLED':
      return true;
    case 'EXTERNAL':
      return !(hideNoise && node.role === 'NOISE');
    case 'VARIABLE':
    case 'OBJ_VARIABLE':
      return node.fieldOwner !== undefined;
    case 'BIN_OP':
      return decisions.has(node.id);
    case 'LITERAL':
    case 'BASE':
      return false;
  }
}

export type ContractedEdge = {
  source: Id;
  target: Id;
  kinds: Partial<Record<EdgeKind, number>>;
  count: number;
  contractedIds: Id[];
  caption?: string;
};

export type ContractedGraph = {
  anchors: PayloadNode[];
  edges: ContractedEdge[];
  decisions: Set<Id>;
};

/**
 * Exact expression contraction over a payload (Item 1).
 *
 * Replaces maximal paths of non-anchor nodes between anchor endpoints with direct derived edges
 * carrying the contracted intermediate node IDs. Preserves exact dataflow reachability between
 * every pair of anchors.
 */
export function contractExpressions(
  payload: Payload,
  options: { hideNoise?: boolean; expanded?: Set<Id> } = {},
): ContractedGraph {
  const hideNoise = options.hideNoise ?? true;
  const expanded = options.expanded;
  const decisions = decisionIds(payload);
  const byId = new Map(payload.nodes.map((n) => [n.id, n]));
  const anchors = payload.nodes.filter((n) => isAnchor(n, decisions, hideNoise, expanded));
  const anchorSet = new Set(anchors.map((n) => n.id));

  const outgoing = new Map<Id, PayloadEdge[]>();
  for (const edge of payload.edges) {
    if (!outgoing.has(edge.source)) outgoing.set(edge.source, []);
    outgoing.get(edge.source)!.push(edge);
  }

  const merged = new Map<string, {
    source: Id;
    target: Id;
    kinds: Partial<Record<EdgeKind, number>>;
    count: number;
    intermediates: Set<Id>;
  }>();

  const record = (source: Id, target: Id, kind: EdgeKind, mids: Id[]) => {
    if (source === target) return;
    const key = `${source}\u0000${target}`;
    let entry = merged.get(key);
    if (!entry) {
      entry = { source, target, kinds: {}, count: 0, intermediates: new Set() };
      merged.set(key, entry);
    }
    entry.kinds[kind] = (entry.kinds[kind] ?? 0) + 1;
    entry.count += 1;
    for (const id of mids) entry.intermediates.add(id);
  };

  for (const start of anchors) {
    const firstEdges = outgoing.get(start.id) ?? [];
    for (const first of firstEdges) {
      if (anchorSet.has(first.target)) {
        record(start.id, first.target, first.kind, []);
        continue;
      }
      // BFS across non-anchor intermediates only. Do not cross EVENT edges inside a data walk,
      // and treat noise nodes when hidden as transparent pass-throughs (not endpoints).
      const isEventWalk = first.kind === 'EVENT';
      const visited = new Set<Id>([first.target]);
      const queue: Array<{ id: Id; path: Id[] }> = [{ id: first.target, path: [first.target] }];
      // Track which (targetAnchor, kind) pairs were already reached from this `first` edge to avoid
      // exponential multiplicity across diamond sub-expressions while still collecting intermediates.
      const reached = new Map<string, Set<Id>>();

      while (queue.length > 0) {
        const current = queue.shift()!;
        for (const next of outgoing.get(current.id) ?? []) {
          if (next.kind === 'EVENT' && !isEventWalk) continue;
          if (anchorSet.has(next.target)) {
            const effectiveKind: EdgeKind = isEventWalk
              ? 'EVENT'
              : (next.kind !== 'FLOW' ? next.kind : first.kind);
            const rk = `${next.target}\u0000${effectiveKind}`;
            let mids = reached.get(rk);
            if (!mids) {
              mids = new Set();
              reached.set(rk, mids);
            }
            for (const p of current.path) mids.add(p);
          } else if (!visited.has(next.target)) {
            visited.add(next.target);
            queue.push({ id: next.target, path: [...current.path, next.target] });
          }
        }
      }

      for (const [rk, mids] of reached) {
        const [targetId, kind] = rk.split('\u0000') as [Id, EdgeKind];
        record(start.id, targetId, kind, [...mids]);
      }
    }
  }

  const edges: ContractedEdge[] = [...merged.values()].map((e) => {
    const contractedIds = [...e.intermediates];
    let caption: string | undefined;
    if (contractedIds.length > 0 && !(Object.keys(e.kinds).length === 1 && e.kinds.EVENT)) {
      caption = derivedCaption(payload, byId, contractedIds);
    }
    return {
      source: e.source,
      target: e.target,
      kinds: e.kinds,
      count: e.count,
      contractedIds,
      ...(caption === undefined ? {} : { caption }),
    };
  });

  return { anchors, edges, decisions };
}

/** Concise derived label for a contracted chain of non-anchor expression nodes. */
function derivedCaption(payload: Payload, byId: Map<Id, PayloadNode>, mids: Id[]): string | undefined {
  const ops = mids.map((id) => byId.get(id)).filter((n): n is PayloadNode => n !== undefined && n.type !== 'LITERAL');
  const interesting = ops.length > 0 ? ops : mids.map((id) => byId.get(id)).filter((n): n is PayloadNode => n !== undefined);
  // Prefer a short expression from the last intermediate's source line if it is a single concise RHS.
  const last = interesting[interesting.length - 1];
  const line = last ? sourceLine(payload, last.source) : null;
  if (line !== null) {
    const rhs = /=\s*([^;]+);?$/.exec(line)?.[1]?.trim();
    if (rhs && rhs.length <= 26) return clip(rhs, 26);
  }
  const labels = [...new Set(interesting.map((n) => n.label))].slice(0, 3).join(' ');
  return labels.length > 0 ? clip(`${labels} (${mids.length})`, 28) : `${mids.length} expr`;
}

/** Simple class name from a qualified name (`pkg.Outer.Loan` -> `Loan`). */
function simpleClass(qualified: string): string {
  return qualified.slice(qualified.lastIndexOf('.') + 1);
}

/**
 * Level 2 — Method Workflow View (Items 1, 2, 3, 5, 6).
 *
 * Shows the active method(s) with expression contraction applied: anchors, readable decision nodes,
 * domain field accesses, typed boundaries, exceptions, and folded callee Summary Cards. Clicking a
 * folded callee opens it; clicking a contracted edge expands its collapsed expression nodes.
 */
export function workflowView(
  payload: Payload,
  options: { hideNoise?: boolean; expanded?: Set<Id>; focusBox?: Id } = {},
): CondensedView {
  const hideNoise = options.hideNoise ?? true;
  const expanded = options.expanded ?? new Set<Id>();
  const byId = new Map(payload.nodes.map((n) => [n.id, n]));
  const methodBoxes = payload.nodes.filter((n) => n.type === 'METHOD');
  const roots = methodBoxes.filter((n) => n.parent === undefined);

  const openBoxes = new Set<Id>();
  if (options.focusBox && byId.get(options.focusBox)?.type === 'METHOD') {
    openBoxes.add(options.focusBox);
  } else {
    for (const r of roots) openBoxes.add(r.id);
  }
  for (const id of expanded) {
    const node = byId.get(id);
    if (node?.type === 'METHOD') openBoxes.add(id);
    else if (node?.parent !== undefined) openBoxes.add(node.parent);
  }

  const contracted = contractExpressions(payload, { hideNoise, expanded });

  // Map every anchor to the display node representing it: itself if its box is open, else its
  // highest closed ancestor method box (Summary Card).
  const displayBoxFor = (leafBoxId: Id): Id | null => {
    if (openBoxes.has(leafBoxId)) return null;
    let cur: Id | undefined = leafBoxId;
    let highestClosed: Id | null = null;
    while (cur !== undefined) {
      if (openBoxes.has(cur)) break;
      highestClosed = cur;
      cur = byId.get(cur)?.parent;
    }
    return highestClosed;
  };

  // If focusBox is set, restrict to anchors inside focusBox (and its descendants) plus nodes directly connected to them.
  const inSubtree = (boxId: Id | undefined, ancestor: Id): boolean => {
    let cur = boxId;
    while (cur !== undefined) {
      if (cur === ancestor) return true;
      cur = byId.get(cur)?.parent;
    }
    return false;
  };

  const activeAnchors = options.focusBox
    ? contracted.anchors.filter((a) => inSubtree(a.parent, options.focusBox!))
    : contracted.anchors;
  const activeAnchorSet = new Set(activeAnchors.map((a) => a.id));

  const nodes: CondensedNode[] = [];
  const frames: CondensedFrame[] = [];
  const frameByBox = new Map<Id, Id>();
  const closedCardCount = new Map<Id, number>();

  for (const anchor of activeAnchors) {
    if (anchor.parent === undefined) continue;
    const closedBox = displayBoxFor(anchor.parent);
    if (closedBox !== null) {
      closedCardCount.set(closedBox, (closedCardCount.get(closedBox) ?? 0) + 1);
      continue;
    }
    if (!frameByBox.has(anchor.parent)) {
      const box = byId.get(anchor.parent)!;
      const fid = `frame:${box.id}`;
      frameByBox.set(box.id, fid);
      frames.push({
        id: fid,
        box: box.id,
        label: box.label,
        ...(box.boundary ? { boundary: true as const } : {}),
        ...(box.assumed ? { assumed: true as const } : {}),
      });
    }
    const isDec = contracted.decisions.has(anchor.id);
    const label = isDec
      ? decisionCaption(payload, anchor)
      : (anchor.fieldOwner !== undefined ? `${simpleClass(anchor.fieldOwner)}.${anchor.label}` : anchor.label);
    nodes.push({
      id: anchor.id,
      label,
      type: isDec ? 'DECISION' : anchor.type,
      ...(anchor.lead === undefined ? {} : { lead: anchor.lead }),
      ...(anchor.role === undefined ? {} : { role: anchor.role }),
      frame: frameByBox.get(anchor.parent)!,
    });
  }

  // Emit Summary Cards for closed callee method boxes.
  for (const box of methodBoxes) {
    const count = closedCardCount.get(box.id);
    if (!count) continue;
    if (hideNoise && box.boundaryRole === 'NOISE') continue;
    const sub = summaryLines(box.summary, box.assumed);
    nodes.push({
      id: box.id,
      label: box.method ? shortMethod(box.method) : box.label,
      type: 'GROUP',
      members: count,
      ...(box.boundary ? { boundary: true } : {}),
      ...(box.assumed ? { assumed: true } : {}),
      ...(box.boundaryRole ?? box.classRole ? { role: box.boundaryRole ?? box.classRole } : {}),
      ...(sub.length > 0 ? { subtitle: sub } : {}),
    });
  }

  const drawnIds = new Set(nodes.map((n) => n.id));
  const shownOf = (anchorId: Id): Id | null => {
    if (!activeAnchorSet.has(anchorId)) return null;
    if (drawnIds.has(anchorId)) return anchorId;
    const parent = byId.get(anchorId)?.parent;
    if (parent === undefined) return null;
    const closed = displayBoxFor(parent);
    return closed !== null && drawnIds.has(closed) ? closed : null;
  };

  const edgeMap = new Map<string, CondensedEdge>();
  for (const edge of contracted.edges) {
    const s = shownOf(edge.source);
    const t = shownOf(edge.target);
    if (s === null || t === null || s === t) continue;
    // Drop EVENT edges into folded boxes unless it is the entry's direct start.
    const onlyEvent = Object.keys(edge.kinds).length === 1 && edge.kinds.EVENT !== undefined;
    if (onlyEvent && !drawnIds.has(edge.target)) continue;
    const key = `${s}\u0000${t}`;
    const existing = edgeMap.get(key);
    if (existing) {
      existing.count += edge.count;
      for (const [k, v] of Object.entries(edge.kinds) as Array<[EdgeKind, number]>) {
        existing.kinds[k] = (existing.kinds[k] ?? 0) + v;
      }
      if (edge.contractedIds.length > 0) {
        existing.contractedIds = [...new Set([...(existing.contractedIds ?? []), ...edge.contractedIds])];
      }
      if (!existing.caption && edge.caption) existing.caption = edge.caption;
    } else {
      edgeMap.set(key, {
        source: s,
        target: t,
        kinds: { ...edge.kinds },
        count: edge.count,
        ...(edge.caption === undefined ? {} : { caption: edge.caption }),
        ...(edge.contractedIds.length > 0 ? { contractedIds: [...edge.contractedIds] } : {}),
      });
    }
  }

  return { nodes, edges: [...edgeMap.values()], frames };
}

/**
 * Level 1 — Architecture / Effect Map (Items 2, 3, 4, 5, 6).
 *
 * Shows Entrypoints -> Service/Component Summary Cards (with `assumed` single-impl badges) ->
 * first-class Domain Entity State Cards (`ENTITY`), Typed Boundaries (`DB`, `HTTP`, `EVENT`),
 * Exceptions (`THROW`), and `UNMODELLED` gaps. Zero locals, zero operators.
 */
export function architectureView(
  payload: Payload,
  options: { hideNoise?: boolean } = {},
): CondensedView {
  const hideNoise = options.hideNoise ?? true;
  const byId = new Map(payload.nodes.map((n) => [n.id, n]));
  const methodBoxes = payload.nodes.filter((n) => n.type === 'METHOD');
  const boxIds = new Set(methodBoxes.map((m) => m.id));

  const nodes: CondensedNode[] = [];
  const addedNodes = new Set<Id>();
  const addNode = (node: CondensedNode) => {
    if (addedNodes.has(node.id)) return;
    addedNodes.add(node.id);
    nodes.push(node);
  };

  const edgeMap = new Map<string, CondensedEdge>();
  const addEdge = (source: Id, target: Id, kind: EdgeKind, caption?: string) => {
    if (source === target || !addedNodes.has(source) || !addedNodes.has(target)) return;
    const key = `${source}\u0000${target}`;
    const existing = edgeMap.get(key);
    if (existing) {
      existing.count += 1;
      existing.kinds[kind] = (existing.kinds[kind] ?? 0) + 1;
      if (!existing.caption && caption) existing.caption = caption;
    } else {
      edgeMap.set(key, { source, target, kinds: { [kind]: 1 }, count: 1, ...(caption ? { caption } : {}) });
    }
  };

  // 1. Entrypoints and Method / Component Summary Cards
  const includedBoxes = new Set<Id>();
  for (const box of methodBoxes) {
    if (hideNoise && box.boundaryRole === 'NOISE') continue;
    includedBoxes.add(box.id);
    const sub = summaryLines(box.summary, box.assumed);
    addNode({
      id: box.id,
      label: box.method ? shortMethod(box.method) : box.label,
      type: 'GROUP',
      members: payload.nodes.filter((n) => n.parent === box.id && n.type !== 'METHOD').length,
      ...(box.boundary ? { boundary: true } : {}),
      ...(box.assumed ? { assumed: true } : {}),
      ...(box.boundaryRole ?? box.classRole ? { role: box.boundaryRole ?? box.classRole } : {}),
      ...(sub.length > 0 ? { subtitle: sub } : {}),
    });
  }

  for (const node of payload.nodes) {
    if (node.type === 'EVENT' && node.parent !== undefined && includedBoxes.has(node.parent)) {
      const rootBox = byId.get(node.parent);
      addNode({
        id: node.id,
        label: rootBox?.entry ?? node.label,
        type: 'EVENT',
      });
    }
  }

  // 2. Domain Entity State Cards (Item 4): aggregate reads & writes by `fieldOwner`.
  const entityReads = new Map<string, Set<string>>();
  const entityWrites = new Map<string, Set<string>>();
  const boxEntityWrites = new Map<string, Set<string>>(); // `${boxId}\0${owner}` -> field names
  const boxEntityReads = new Map<string, Set<string>>();

  const recordField = (map: Map<string, Set<string>>, key: string, field: string) => {
    if (!map.has(key)) map.set(key, new Set());
    map.get(key)!.add(field);
  };

  for (const node of payload.nodes) {
    if (node.fieldOwner === undefined || node.parent === undefined || !includedBoxes.has(node.parent)) continue;
    const owner = node.fieldOwner;
    if (node.fieldOp === 'read') {
      recordField(entityReads, owner, node.label);
      recordField(boxEntityReads, `${node.parent}\u0000${owner}`, node.label);
    } else if (node.fieldOp === 'write' || node.fieldOp === 'join') {
      recordField(entityWrites, owner, node.label);
      recordField(boxEntityWrites, `${node.parent}\u0000${owner}`, node.label);
    }
  }
  // Cross-box reads: a field node written in box A feeding a node in box B means B read that field.
  for (const edge of payload.edges) {
    if (edge.kind === 'EVENT' || edge.kind === 'ORDER') continue;
    const src = byId.get(edge.source);
    const dst = byId.get(edge.target);
    if (src?.fieldOwner && dst?.parent && includedBoxes.has(dst.parent) && dst.parent !== src.parent) {
      recordField(entityReads, src.fieldOwner, src.label);
      recordField(boxEntityReads, `${dst.parent}\u0000${src.fieldOwner}`, src.label);
    }
  }

  // Classes annotated as controllers/services/repositories hold injected collaborator fields
  // (e.g. `private LoanService service;`) that are DI wiring, not domain entities; skip them as
  // ENTITY cards unless they have actual state writes.
  const componentClasses = new Set<string>();
  for (const box of methodBoxes) {
    if (box.method && (box.classRole === 'CONTROLLER' || box.classRole === 'SERVICE' || box.classRole === 'REPOSITORY')) {
      componentClasses.add(box.method.split('#')[0]);
    }
  }

  const allOwners = new Set([...entityReads.keys(), ...entityWrites.keys()]);
  for (const owner of allOwners) {
    const writes = [...(entityWrites.get(owner) ?? [])];
    const reads = [...(entityReads.get(owner) ?? [])];
    if (componentClasses.has(owner) && writes.length === 0) continue;
    const sub: string[] = [];
    if (writes.length > 0) sub.push(`writes: ${writes.slice(0, SUMMARY_CAP).join(', ')}${writes.length > SUMMARY_CAP ? ` +${writes.length - SUMMARY_CAP}` : ''}`);
    if (reads.length > 0) sub.push(`reads: ${reads.slice(0, SUMMARY_CAP).join(', ')}${reads.length > SUMMARY_CAP ? ` +${reads.length - SUMMARY_CAP}` : ''}`);
    addNode({
      id: `entity:${owner}`,
      label: simpleClass(owner),
      type: 'ENTITY',
      role: 'ENTITY',
      ...(sub.length > 0 ? { subtitle: sub } : {}),
    });
  }

  // 3. External Boundaries (outside boundary METHOD boxes), Throws, and Unmodelled Gaps
  const outgoing = new Map<Id, PayloadEdge[]>();
  for (const edge of payload.edges) {
    if (!outgoing.has(edge.source)) outgoing.set(edge.source, []);
    outgoing.get(edge.source)!.push(edge);
  }
  const extLinks: Array<{ fromBox: Id; extId: Id }> = [];
  const throwLinks: Array<{ fromBox: Id; throwId: Id }> = [];
  const gapLinks: Array<{ fromBox: Id; gapId: Id }> = [];

  for (const node of payload.nodes) {
    if (node.parent === undefined || !includedBoxes.has(node.parent)) continue;
    const parentBox = byId.get(node.parent);
    if (node.type === 'EXTERNAL') {
      if (parentBox?.boundary) continue; // already represented by its boundary METHOD card
      if (node.fieldOwner !== undefined) continue; // already represented by its ENTITY card
      const outs = outgoing.get(node.id) ?? [];
      if (outs.length > 0 && outs.every((e) => byId.get(e.target)?.type === 'THROW')) continue; // exception ctor feeding THROW
      if (hideNoise && node.role === 'NOISE') continue;
      const rolePrefix = node.role ? `${node.role}: ` : '';
      const clean = node.label.replace(/^call\s+/, '');
      const extId = `ext:${node.role ?? 'EXT'}:${clean}`;
      addNode({
        id: extId,
        label: `${rolePrefix}${clean}`,
        type: 'EXTERNAL',
        ...(node.role ? { role: node.role } : {}),
      });
      extLinks.push({ fromBox: node.parent, extId });
    } else if (node.type === 'THROW') {
      const exName = node.label.replace(/^throw\s+/, '');
      const throwId = `throw:${exName}`;
      addNode({ id: throwId, label: `throw ${exName}`, type: 'THROW', lead: 'throw' });
      throwLinks.push({ fromBox: node.parent, throwId });
    } else if (node.type === 'UNMODELLED') {
      const gapId = `gap:${node.label}`;
      addNode({ id: gapId, label: node.label, type: 'UNMODELLED' });
      gapLinks.push({ fromBox: node.parent, gapId });
    }
  }

  // 4. Wire edges: Entrypoints -> Roots, Caller -> Callee, Method <-> Entity, Method -> Boundary/Throw/Gap, SHARED
  for (const node of payload.nodes) {
    if (node.type === 'EVENT' && node.parent !== undefined && includedBoxes.has(node.parent)) {
      addEdge(node.id, node.parent, 'EVENT');
    }
  }
  for (const box of methodBoxes) {
    if (!includedBoxes.has(box.id)) continue;
    if (box.parent !== undefined && includedBoxes.has(box.parent)) {
      addEdge(box.parent, box.id, box.boundary ? 'ORDER' : 'FLOW', box.assumed ? 'assumed' : undefined);
    }
  }
  for (const [key, fields] of boxEntityWrites) {
    const [boxId, owner] = key.split('\u0000');
    const names = [...fields].slice(0, 2).join(', ') + (fields.size > 2 ? ` +${fields.size - 2}` : '');
    addEdge(boxId, `entity:${owner}`, 'FLOW', `writes ${names}`);
  }
  for (const [key, fields] of boxEntityReads) {
    const [boxId, owner] = key.split('\u0000');
    const names = [...fields].slice(0, 2).join(', ') + (fields.size > 2 ? ` +${fields.size - 2}` : '');
    addEdge(`entity:${owner}`, boxId, 'FLOW', `reads ${names}`);
  }
  for (const { fromBox, extId } of extLinks) addEdge(fromBox, extId, 'FLOW');
  for (const { fromBox, throwId } of throwLinks) addEdge(fromBox, throwId, 'FALSE');
  for (const { fromBox, gapId } of gapLinks) addEdge(fromBox, gapId, 'CONDITION');

  // Preserve SHARED edges between roots/methods.
  for (const edge of payload.edges) {
    if (edge.kind !== 'SHARED') continue;
    const sBox = byId.get(edge.source)?.parent;
    const tBox = byId.get(edge.target)?.parent;
    if (sBox && tBox && boxIds.has(sBox) && boxIds.has(tBox) && sBox !== tBox) {
      addEdge(sBox, tBox, 'SHARED', 'shared');
    }
  }

  return { nodes, edges: [...edgeMap.values()], frames: [] };
}

/** Hover tooltip text for Architecture and Workflow views. */
export function highLevelHoverText(payload: Payload, view: CondensedView, target: CondensedTarget): string[] {
  const byId = new Map(payload.nodes.map((n) => [n.id, n]));
  if ('edge' in target) {
    const [s, t] = target.edge;
    const edge = view.edges.find((e) => e.source === s && e.target === t);
    if (!edge) return [];
    const lines: string[] = [];
    if (edge.caption) lines.push(edge.caption);
    const kinds = Object.entries(edge.kinds).map(([k, n]) => `${k.toLowerCase()} ×${n}`);
    if (kinds.length > 0) lines.push(kinds.join(', '));
    if (edge.contractedIds && edge.contractedIds.length > 0) {
      lines.push(`click to expand ${edge.contractedIds.length} contracted expression${edge.contractedIds.length === 1 ? '' : 's'}`);
      for (const id of edge.contractedIds.slice(0, 6)) {
        const node = byId.get(id);
        if (!node) continue;
        const text = sourceLine(payload, node.source);
        lines.push(`  ${node.label}${text ? `  (${clip(text, 44)})` : ''}`);
      }
      if (edge.contractedIds.length > 6) lines.push(`  +${edge.contractedIds.length - 6} more`);
    }
    return lines;
  }

  const drawn = view.nodes.find((n) => n.id === target.node);
  const raw = byId.get(target.node);
  const lines: string[] = [];
  if (drawn) {
    const badge = drawn.role ? ` [${drawn.role}]` : '';
    lines.push(`${drawn.label}${badge}`);
    if (drawn.subtitle) lines.push(...drawn.subtitle);
  }
  if (raw?.method) lines.push(raw.method);
  const at = where(raw?.source);
  if (at) {
    const text = payload.lines?.[at.file]?.[at.line];
    lines.push(`${at.name}:${at.line}${text ? `  ${clip(text, 50)}` : ''}`);
  }
  return lines;
}
