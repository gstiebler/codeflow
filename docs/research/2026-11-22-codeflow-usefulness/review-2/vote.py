"""vote.py sum.json <regex over method name> [min_flows] — sibling majority vote over ext-arg sink names.
For each family member: sinks that at least `quorum` (all but one, min 2) of the OTHER members have and it lacks."""
import json, re, sys
doc = json.load(open(sys.argv[1])); rx = re.compile(sys.argv[2]); minflows = int(sys.argv[3]) if len(sys.argv) > 3 else 100
fam = {}
for m in doc['methods']:
    name = m['method'].split('#', 1)[1]
    if rx.search(name.split('(')[0]) and len(m['flows']) >= minflows:
        sinks = {re.sub(r'@[^@]*$', '', d)[8:] for _, d in m['flows'] if d.startswith('ext-arg:')}
        fam[name.split('(')[0] + '/' + str(name.count(',') + 1)] = (sinks, len(m['gaps']))
print(f'family of {len(fam)}:', ', '.join(f'{k} ({len(v[0])} sinks, {v[1]} gaps)' for k, v in fam.items()))
for k, (sinks, gaps) in fam.items():
    others = [v[0] for kk, v in fam.items() if kk != k]
    quorum = max(2, len(others) - 1)
    missing = sorted(s for s in set().union(*others) if s not in sinks and sum(s in o for o in others) >= quorum)
    print(f'\n{k}: lacks {len(missing)} sinks that >= {quorum} of {len(others)} siblings have')
    print('  ', missing)
