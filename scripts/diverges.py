#!/usr/bin/env python3
"""What one sibling does and another does not, read off a `codeflow --summaries` document.

  diverges.py SUMMARIES.json overrides:NAME       methods sharing a simple name and a parameter list
  diverges.py SUMMARIES.json stem:GLOB            methods whose simple name matches
  diverges.py SUMMARIES.json METHOD METHOD ...    methods named one by one, each a part of exactly one id
  diverges.py SUMMARIES.json fields:CLASS         methods touching most fields of CLASS and missing some
  diverges.py SUMMARIES.json sink:LABEL[@AT]      methods something reaches the call LABEL in, by what feeds it

SUMMARIES.json may be - for stdin. A row is an effect at least one sibling lacks:

  write F     a value flows into field F
  call L(F)   field F reaches the call L, as its receiver or as an argument: a summary does not say which
  call L      anything reaches the call L
  read F      a value flows out of field F

Rows are grouped by the siblings lacking them: `in 1,2,4, not in 3`. A ? after a number means that
sibling has gaps: the summary did not read all of it, so the effect is not known to be absent.

sink: lists the ways one call is fed: a field, the result of another call `g()`, or a parameter. A method
is listed under the call's own method too when it reaches it, so a caller shares its callee's way unless
it adds a feed. AT is the call's position as the document prints it, for a label several calls share.
"""
import argparse
import fnmatch
import functools
import itertools
import json
import re
import sys
from collections import Counter, namedtuple

Field = namedtuple('Field', 'cls name')
Effect = namedtuple('Effect', 'kind label field')  # write | call | read; a label only on a call
Row = namedtuple('Row', 'effect present absent unknown at')
Found = namedtuple('Found', 'method touched missed gaps')
Fed = namedtuple('Fed', 'common ways')  # what feeds the call in every method, then each way it is fed
Way = namedtuple('Way', 'feeds methods')  # the feeds beyond the common ones, and the methods fed so

HOLDER = re.compile(r'^(?:this|static|RET|P\d+)\.(.+)$')
FIELD_GAP = re.compile(r'^(\S+) (read|written) (?:on an object with no tracked origin|by \S+ on no object this can name)$')
# Orders rows, never hides one.
MUTATOR = re.compile(r'^(put|add|remove|set|clear|compute|merge|replace|offer|push|pop|poll|insert|delete|update|append'
                     r'|retain|reset|increment|decrement)([A-Z_].*)?$')


def load(path):
    with (sys.stdin if path == '-' else open(path)) as source:
        doc = json.load(source)
    if (doc.get('format'), doc.get('version')) != ('codeflow-summaries', 1):
        raise ValueError(f"not a codeflow-summaries version 1 document: {doc.get('format')!r} version {doc.get('version')!r}")
    return doc


def class_names(doc):
    return frozenset(m['method'].split('#')[0] for m in doc['methods'])


@functools.lru_cache(maxsize=None)
def links(path, classes):
    """`a.C.f.a.D.g` as (a.C f, a.D g). A summary prints a field path as one dotted string, so a link is
    the longest class the document names, then one name; a class it does not name ends where the next
    named one starts, or at the last dot."""
    parts = path.split('.')

    def named(i):
        return next((k for k in range(len(parts) - 1, i, -1) if '.'.join(parts[i:k]) in classes), None)

    found, i = [], 0
    while i < len(parts):
        k = named(i) or next((j - 1 for j in range(i + 2, len(parts)) if named(j)), len(parts) - 1)
        found.append(Field('.'.join(parts[i:k]), parts[k]))
        i = k + 1
    return tuple(found)


def effects(method, classes):
    """What one method does, and where: {Effect: [position, ...]}."""
    found = {}

    def add(kind, label, field, at=None):
        places = found.setdefault(Effect(kind, label, field), [])
        if at and at not in places:
            places.append(at)

    def read(path, at=None):
        for field in path:
            add('read', None, field, at)

    def write(path, at=None):
        read(path[:-1], at)
        add('write', None, path[-1], at)

    def held(atom):
        path = HOLDER.match(atom)
        return links(path.group(1), classes) if path else ()

    for source, target in method['flows']:
        out_of = held(source)
        read(out_of)
        if target.startswith('ext-arg:'):
            label, _, at = target[len('ext-arg:'):].rpartition('@')
            add('call', label, None, at)
            if out_of:
                add('call', label, out_of[-1], at)
        elif held(target):
            write(held(target))
    for gap in method['gaps']:
        named = FIELD_GAP.match(gap['detail'])
        if named:
            path = held(named.group(1)) or links(named.group(1), classes)
            (read if named.group(2) == 'read' else write)(path, gap['at'])
    return found


def simple_name(method):
    name = method['method']
    return name[name.index('#') + 1:name.index('(')]


def select(doc, selectors):
    """The families a query names: lists of methods to compare with each other."""
    methods = doc['methods']
    kind, _, name = selectors[0].partition(':')
    if kind == 'overrides' and len(selectors) == 1:
        by_parameters = {}
        for m in methods:
            if simple_name(m) == name:
                by_parameters.setdefault(m['method'][m['method'].index('('):], []).append(m)
        families = [family for family in by_parameters.values() if len(family) > 1]
        if not families:
            raise ValueError(f'no two methods named {name} share a parameter list')
        return families
    if kind == 'stem' and len(selectors) == 1:
        family = [m for m in methods if fnmatch.fnmatchcase(simple_name(m), name)]
    else:
        family = [only([m for m in methods if s in m['method']], s, 'method') for s in selectors]
    if len(family) < 2:
        raise ValueError(f"{' '.join(selectors)} names {len(family)} method, and a comparison needs two")
    return [family]


def only(matches, name, what):
    if len(matches) != 1:
        listed = ', '.join(m if isinstance(m, str) else m['method'] for m in matches) or 'none'
        raise ValueError(f'{name} names {len(matches)} {what}s, not one: {listed}')
    return matches[0]


def rank(effect):
    if effect.kind == 'write':
        return 0
    if effect.kind == 'read':
        return 4
    if MUTATOR.match(effect.label):
        return 1 if effect.field else 2
    return 3


def compare(doc, family):
    """One Row per effect some sibling lacks. What the fewest siblings lack comes first, a firm absence before
    an unknown one, and the rows the same siblings lack stay together."""
    classes = class_names(doc)
    done = {m['method']: effects(m, classes) for m in family}
    order = {name: i for i, name in enumerate(done)}
    gapped = {m['method'] for m in family if m['gaps']}
    rows = []
    for effect in {e for found in done.values() for e in found}:
        lacking = [name for name, found in done.items() if effect not in found]
        if lacking:
            rows.append(Row(effect,
                            present=[name for name in done if name not in lacking],
                            absent=[name for name in lacking if name not in gapped],
                            unknown=[name for name in lacking if name in gapped],
                            at={name: found[effect] for name, found in done.items() if found.get(effect)}))
    return sorted(rows, key=lambda r: (len(r.absent) + len(r.unknown), len(r.unknown),
                                       sorted(order[name] for name in r.absent + r.unknown),
                                       rank(r.effect), r.effect.label or '', r.effect.field or Field('', '')))


def fields(doc, name, share=0.5):
    """The methods reading or writing at least `share` of a class's fields, and not all of them. A class's
    fields are the ones some method of the document reads or writes."""
    classes = class_names(doc)
    touched = {m['method']: {e.field for e in effects(m, classes) if e.kind != 'call'} for m in doc['methods']}
    seen = {field for mine in touched.values() for field in mine}
    known = classes | {field.cls for field in seen}
    cls = only(sorted(c for c in known if c == name or c.endswith('.' + name)), name, 'class')
    its = sorted(field for field in seen if field.cls == cls)
    if not its:
        raise ValueError(f'no field of {cls} is read or written in this document')
    found = []
    for m in doc['methods']:
        mine = [field for field in its if field in touched[m['method']]]
        if share * len(its) <= len(mine) < len(its):
            found.append(Found(m['method'], mine, [field for field in its if field not in mine], len(m['gaps'])))
    return sorted(found, key=lambda f: (len(f.missed), f.method))


def sink(doc, name):
    """The ways one call is fed. A feed is a Field, the result of a call `g()`, or `a parameter`: which
    parameter is not comparable from one method to the next."""
    classes = class_names(doc)
    label, _, at = name.partition('@')

    def feed(source):
        path = HOLDER.match(source)
        if path:
            return links(path.group(1), classes)[-1]
        if source.startswith('ext:'):
            return source[len('ext:'):].rpartition('@')[0] + '()'
        return 'a parameter' if re.fullmatch(r'P\d+', source) else source

    def reaches(target):
        return target == f'ext-arg:{name}' if at else target.startswith(f'ext-arg:{label}@')

    fed = [(m, {feed(source) for source, target in m['flows'] if reaches(target)}) for m in doc['methods']]
    fed = [(m, feeds) for m, feeds in fed if feeds]
    if not fed:
        raise ValueError(f'nothing reaches a call {name} in this document')
    common = set.intersection(*(feeds for _, feeds in fed))
    ways = {}
    for m, feeds in fed:
        ways.setdefault(tuple(sorted(feeds - common, key=feed_text)), []).append(m)
    least_first = sorted(ways.items(), key=lambda way: (len(way[0]), [feed_text(f) for f in way[0]]))
    return Fed(sorted(common, key=feed_text), [Way(list(feeds), methods) for feeds, methods in least_first])


def feed_text(feed):
    return feed if isinstance(feed, str) else qualified(feed)


def qualified(field):
    return f'{field.cls}.{field.name}'


# --- printing ---

def short(cls, classes):
    """A class without its package: what precedes the outermost class the document names."""
    parts = cls.split('.')
    outer = next((k for k in range(1, len(parts) + 1) if '.'.join(parts[:k]) in classes), len(parts))
    return '.'.join(parts[outer - 1:])


def short_method(name, classes):
    cls, _, rest = name.partition('#')
    return f'{short(cls, classes)}#{rest}'


def spell(effect, classes):
    field = f'{short(effect.field.cls, classes)}.{effect.field.name}' if effect.field else None
    if effect.kind != 'call':
        return f'{effect.kind} {field}'
    return f'call {effect.label}({field})' if field else f'call {effect.label}'


def count(n, word):
    return f"{n} {word}{'' if n == 1 else 's'}"


def gaps_text(method):
    kinds = Counter(gap['kind'] for gap in method['gaps'])
    if not kinds:
        return 'no gaps'
    return f"{count(len(method['gaps']), 'gap')}: " + ', '.join(f'{n} {kind}' for kind, n in sorted(kinds.items()))


def table(lines):
    widths = [max(len(line[i]) for line in lines) for i in range(len(lines[0]))]
    return [''.join(cell.ljust(width + 2) for cell, width in zip(line, widths)).rstrip() for line in lines]


def family_text(query, family, rows, classes):
    number = {m['method']: str(i) for i, m in enumerate(family, 1)}
    out = [f"diverges {query}: {count(len(family), 'sibling')}, {count(len(rows), 'effect')} not in all of them", '']
    out += [f"{number[m['method']]} = {line}" for m, line in zip(family, table(
        [(short_method(m['method'], classes), m['at'], gaps_text(m)) for m in family]))]
    for (present, absent, unknown), group in itertools.groupby(rows, key=lambda r: (r.present, r.absent, r.unknown)):
        group = list(group)
        lacking = [number[name] for name in absent] + [number[name] + '?' for name in unknown]
        out += ['', f"in {','.join(number[name] for name in present)}, not in {','.join(lacking)}: {count(len(group), 'effect')}"]
        lines = []
        for r in group:
            places = [at for name in present for at in r.at.get(name, [])]
            lines.append((spell(r.effect, classes), ' '.join(places[:3]) + (f' +{len(places) - 3}' if len(places) > 3 else '')))
        out += ['  ' + line for line in table(lines)]
    return out


def fields_text(query, share, found, by_name, classes):
    out = [f'diverges {query}: {len(found)} found (methods reading or writing at least {share:g} of its fields, and not all)', '']
    lines = [('method', 'fields', 'gaps', 'misses')]
    for f in found:
        lines.append((short_method(f.method, classes), f'{len(f.touched)} of {len(f.touched) + len(f.missed)}',
                      gaps_text(by_name[f.method]), ' '.join(field.name + ('?' if f.gaps else '') for field in f.missed)))
    return out + table(lines)


def sink_text(query, found, classes):
    def spell_feeds(feeds, none):
        return ', '.join(f if isinstance(f, str) else f'{short(f.cls, classes)}.{f.name}' for f in feeds) or none

    methods = sum(len(way.methods) for way in found.ways)
    out = [f"diverges {query}: {count(methods, 'method')}, fed {count(len(found.ways), 'way')}", '',
           f"in all of them: {spell_feeds(found.common, 'nothing')}"]
    for i, way in enumerate(found.ways, 1):
        out += ['', f"{i} = {spell_feeds(way.feeds, 'nothing else')}"]
        out += ['  ' + line for line in table([(short_method(m['method'], classes), m['at'], gaps_text(m)) for m in way.methods])]
    return out


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('summaries', help='a `codeflow --summaries` document, or - for stdin')
    parser.add_argument('selector', nargs='+',
                        help='overrides:NAME | stem:GLOB | fields:CLASS | sink:LABEL[@AT] | METHOD METHOD ...')
    parser.add_argument('--json', action='store_true', help='the rows as JSON')
    parser.add_argument('--min', type=float, default=0.5, metavar='SHARE',
                        help='fields: the share of a class\'s fields a method must touch to be reported (default 0.5)')
    args = parser.parse_args(argv)
    doc = load(args.summaries)
    classes = class_names(doc)
    query = ' '.join(args.selector)
    result = {'format': 'codeflow-diverges', 'version': 1, 'query': args.selector}
    if query.startswith('fields:'):
        found = fields(doc, query[len('fields:'):], args.min)
        result['found'] = [{'method': f.method, 'touched': [qualified(x) for x in f.touched],
                            'missed': [qualified(x) for x in f.missed], 'gaps': f.gaps} for f in found]
        text = fields_text(query, args.min, found, {m['method']: m for m in doc['methods']}, classes)
    elif query.startswith('sink:'):
        found = sink(doc, query[len('sink:'):])
        result['all'] = [feed_text(f) for f in found.common]
        result['ways'] = [{'feeds': [feed_text(f) for f in way.feeds],
                           'methods': [{'method': m['method'], 'at': m['at'], 'gaps': len(m['gaps'])} for m in way.methods]}
                          for way in found.ways]
        text = sink_text(query, found, classes)
    else:
        result['families'], text = [], []
        for family in select(doc, args.selector):
            rows = compare(doc, family)
            result['families'].append({
                'siblings': [{'method': m['method'], 'at': m['at'], 'gaps': m['gaps']} for m in family],
                'rows': [{'effect': spell(r.effect, classes), 'kind': r.effect.kind, 'label': r.effect.label,
                          'field': qualified(r.effect.field) if r.effect.field else None,
                          'present': r.present, 'absent': r.absent, 'unknown': r.unknown, 'at': r.at} for r in rows]})
            text += family_text(query, family, rows, classes) + ['']
    print(json.dumps(result, indent=1) if args.json else '\n'.join(text).rstrip('\n'))


if __name__ == '__main__':
    main(sys.argv[1:])
