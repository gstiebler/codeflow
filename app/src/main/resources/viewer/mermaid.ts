/**
 * The condensed view as a Mermaid flowchart: what is on screen now, after the reader's clicks, to
 * paste into a note, a doc or a PR.
 *
 * Same contract as condensed.ts: no DOM; types, theme.ts and condensed.ts's caption the only imports
 * (the caption is the one the page draws). Its input is the very
 * `condensed(payload, revealed)` the renderer draws, so the file cannot say something the screen
 * does not. Hover text and routes are left out: Mermaid lays the graph out itself.
 */
import { condensedCaption } from './condensed.ts';
import {
  BOUNDARY_OUTLINE, GROUP_FILL, GROUP_OUTLINE, NODE_DEFAULT, NODE_OUTLINE, PALETTE, condensedEdgeStyle,
} from './theme.ts';
import type { CondensedNode, CondensedView } from './types.ts';

/**
 * A theme colour as Mermaid takes it: `#rgb`/`#rrggbb` as they are, `rgba(r, g, b, a)` as
 * `#rrggbbaa`. A classDef is split on commas, so an rgba() inside one would cut it in four.
 */
export function hex(colour: string): string {
  if (/^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$/i.test(colour)) return colour;
  const match = /^rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)$/.exec(colour);
  if (!match) throw new Error(`not a colour Mermaid can take: ${colour}`);
  const byte = (n: number) => n.toString(16).padStart(2, '0');
  const [r, g, b] = match.slice(1, 4).map(Number);
  return `#${byte(r)}${byte(g)}${byte(b)}${byte(Math.round(Number(match[4]) * 255))}`;
}

/** `"` `<` `>` `#` as Mermaid entities; `#` first, or the other three's `#` would be rewritten. */
export function escapeLabel(label: string): string {
  return label.replace(/#/g, '#35;').replace(/"/g, '#quot;').replace(/</g, '#lt;').replace(/>/g, '#gt;');
}

/** What the renderer paints a display node with: a group white in its owner's outline, a leaf its type's fill. */
function look(node: CondensedNode): string {
  if (node.type === 'GROUP') {
    return `fill:${hex(GROUP_FILL)},stroke:${hex(node.boundary ? BOUNDARY_OUTLINE : GROUP_OUTLINE)},stroke-width:2px`;
  }
  return `fill:${hex(PALETTE[node.type] ?? NODE_DEFAULT)},stroke:${hex(NODE_OUTLINE)},stroke-width:1px`;
}

/**
 * `flowchart TD`, nodes `n0, n1, …` in view order (never derived from what they are), one edge line
 * per drawn edge in view order, captioned `×N` when it stands for several, then one classDef per look
 * in use and one linkStyle per edge, indexed in the order the edges were written.
 */
export function condensedMermaid(view: CondensedView): string {
  const id = new Map(view.nodes.map((node, i) => [node.id, `n${i}`]));
  const lines = ['flowchart TD'];

  const classes = new Map<string, { name: string; nodes: string[] }>();
  for (const node of view.nodes) {
    lines.push(`  ${id.get(node.id)}["${escapeLabel(condensedCaption(node))}"]`);
    const style = look(node);
    if (!classes.has(style)) classes.set(style, { name: `c${classes.size}`, nodes: [] });
    classes.get(style)!.nodes.push(id.get(node.id)!);
  }

  const styles: string[] = [];
  view.edges.forEach((edge, i) => {
    const { colour, label, dashed } = condensedEdgeStyle(edge);
    const ends = (end: string) => {
      const at = id.get(end);
      if (at === undefined) throw new Error(`edge endpoint ${end} is not a node of the view`);
      return at;
    };
    lines.push(`  ${ends(edge.source)} -->${label === undefined ? '' : `|"${label}"|`} ${ends(edge.target)}`);
    styles.push(`  linkStyle ${i} stroke:${hex(colour)}${dashed ? ',stroke-dasharray:4 3' : ''}`);
  });

  for (const [style, { name, nodes }] of classes) {
    lines.push(`  classDef ${name} ${style}`, `  class ${nodes.join(',')} ${name}`);
  }
  lines.push(...styles);
  return `${lines.join('\n')}\n`;
}
