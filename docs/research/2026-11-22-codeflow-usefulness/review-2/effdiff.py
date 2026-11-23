"""effdiff.py before.json after.json — per method (matched by name+params, class ignored): which external
call names it feeds (ext-arg:<name>), before vs after. A crude 'effect set' diff that survives a move."""
import json, re, sys
from collections import defaultdict

def effects(path):
    out = defaultdict(set)
    for m in json.load(open(path))['methods']:
        key = m['method'].split('#', 1)[1]
        for _, dst in m['flows']:
            if dst.startswith('ext-arg:'):
                out[key].add(re.sub(r'@[^@]*$', '', dst)[len('ext-arg:'):])
    return out

a, b = effects(sys.argv[1]), effects(sys.argv[2])
changed = 0
for k in sorted(set(a) | set(b)):
    new, gone = b.get(k, set()) - a.get(k, set()), a.get(k, set()) - b.get(k, set())
    if k not in a or k not in b or not (new or gone):
        continue
    changed += 1
    print(k[:90]); 
    if new: print('  NEW ', sorted(new))
    if gone: print('  GONE', sorted(gone))
print(f'{changed} matched methods with a changed effect set; only-before {len(set(a)-set(b))}, only-after {len(set(b)-set(a))}, matched {len(set(a)&set(b))}')
