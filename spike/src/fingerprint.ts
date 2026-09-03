import type { Model } from './types.ts';

/**
 * A name for a statement that survives the version it was written in.
 *
 * The diff compares two builds of the same program, so every statement has to be recognisable on
 * both sides. A `file:line` key is useless for that: Fineract `d602928ae` moves a method 130 lines
 * down and into a different class without touching a line of its body, and keyed by position every
 * statement in it would read as deleted-and-re-added. The whole diff would be noise.
 *
 * So a fingerprint is the enclosing method's **simple** name, the normalised statement text, and an
 * ordinal among identical statements in that method. What is deliberately absent is as important as
 * what is there: no line number, no file, no enclosing class. A rename of the class survives; a move
 * within the file survives; changing the statement's text does not, which is exactly the change a
 * reviewer wants flagged.
 *
 * The ordinal is the price of dropping position. Two identical statements in one method - a
 * `total = total.add(x)` in each arm of an if - are genuinely indistinguishable by text, and the
 * ordinal tells them apart by the order they appear in. It is the one part that a reordering breaks,
 * and reordering two identical statements is a change nobody needs flagged anyway.
 */

/** Statement text with spacing and a trailing semicolon removed, and nothing else. */
export function normalise(code: string): string {
  return code.replace(/\s+/g, ' ').replace(/\s*;\s*$/, '').trim();
}

/** The simple name of a method, from a fullName like `app.api.Api.addCharge:Response(Long)`. */
function simpleMethodName(m: Model, fullName: string): string {
  const known = m.methodByFullName.get(fullName);
  if (known) return known.name;
  return (fullName.split(':')[0] ?? fullName).split('.').pop() ?? fullName;
}

const cache = new WeakMap<Model, Map<string, string>>();

function build(m: Model): Map<string, string> {
  const byMethod = new Map<string, string[]>();
  for (const stmt of m.stmts.values()) {
    if (!byMethod.has(stmt.method)) byMethod.set(stmt.method, []);
    byMethod.get(stmt.method)!.push(stmt.key);
  }

  const out = new Map<string, string>();
  for (const [method, keys] of byMethod) {
    const seen = new Map<string, number>();
    // In source order, so the ordinal means "the second one of these in the method".
    for (const key of keys.sort((a, b) => m.stmts.get(a)!.line - m.stmts.get(b)!.line)) {
      const code = normalise(m.stmts.get(key)!.code);
      const ordinal = seen.get(code) ?? 0;
      seen.set(code, ordinal + 1);
      out.set(key, `${simpleMethodName(m, method)}|${code}#${ordinal}`);
    }
  }
  return out;
}

export function fingerprint(m: Model, key: string): string {
  let table = cache.get(m);
  if (!table) {
    table = build(m);
    cache.set(m, table);
  }
  return table.get(key) ?? `?|${key}#0`;
}
