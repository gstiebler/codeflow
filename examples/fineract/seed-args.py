#!/usr/bin/env python3
"""Print a starting codeflow arguments file for one entry point of a Spring code base.

    examples/fineract/seed-args.py <source-dir> <path/to/Root.java> <method> [more-boundary ...]

The root is `--from Class#method`. The boundaries are the class's injected collaborators: the types
of its `private final` instance fields that are declared somewhere in <source-dir>, written
fully qualified (resolved through the root file's imports, else its own package) so a simple name
two packages share cannot pull in the wrong class. Types outside the sources are listed as comments:
`--boundary` only accepts classes it can read. Extra boundaries (stubs, say) are appended as given.

This only seeds the file. Edit it by hand afterwards; codeflow itself checks every name.
"""
import os, re, sys

src, root_file, method, *extra = sys.argv[1:]
text = open(root_file).read()
cls = os.path.basename(root_file)[:-5]
pkg = re.search(r'^package\s+([\w.]+);', text, re.M).group(1)
imports = {i.rsplit('.', 1)[1]: i for i in re.findall(r'^import\s+([\w.]+);', text, re.M)}

declared = {}  # simple name -> set of qualified names declared in the sources
for d, _, files in os.walk(src, followlinks=True):
    for f in files:
        if f.endswith('.java'):
            m = re.search(r'^package\s+([\w.]+);', open(os.path.join(d, f), errors='replace').read(), re.M)
            if m:
                declared.setdefault(f[:-5], set()).add(f'{m.group(1)}.{f[:-5]}')

fields = re.findall(r'^\s+private\s+final\s+([A-Z]\w*)(?:<[^;=]*>)?\s+\w+\s*;', text, re.M)
print(f'# {cls}#{method}: root, and its injected collaborators as boundaries (seeded by seed-args.py)')
print(f'--from {cls}#{method}')
seen = set()
for t in fields:
    if t in seen:
        continue
    seen.add(t)
    q = imports.get(t, f'{pkg}.{t}')
    if q in declared.get(t, ()):
        print(f'--boundary {q}')
    else:
        print(f'# not in the sources: {t}')
for b in extra:
    print(f'--boundary {b}')
