"""sumdiff.py before.json after.json — per-method flow diff of two --summaries files, positions stripped."""
import json, re, sys
from collections import Counter

def norm(atom):
    return re.sub(r'@[^@]*$', '', atom)

def load(path):
    doc = json.load(open(path))
    out = {}
    for m in doc['methods']:
        out[m['method']] = Counter((norm(a), norm(b)) for a, b in m['flows'])
    return doc['counts'], out

(c1, a), (c2, b) = load(sys.argv[1]), load(sys.argv[2])
print('before', c1); print('after ', c2)
changed = 0
for m in sorted(set(a) | set(b)):
    fa, fb = a.get(m, Counter()), b.get(m, Counter())
    new, gone = fb - fa, fa - fb
    if not new and not gone:
        continue
    changed += 1
    print(m, '(NEW METHOD)' if m not in a else '(GONE METHOD)' if m not in b else '')
    for (s, d), n in sorted(new.items()):
        print('  NEW ', s, '->', d, f'x{n}' if n > 1 else '')
    for (s, d), n in sorted(gone.items()):
        print('  GONE', s, '->', d, f'x{n}' if n > 1 else '')
print(f'{changed} of {len(set(a)|set(b))} methods changed')
