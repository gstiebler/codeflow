#!/usr/bin/env python3
"""q.py <graph.json> nodes <line>            -> nodes whose source line == line
   q.py <graph.json> back <id> [depth] [kinds]  -> backward slice tree
   q.py <graph.json> fwd  <id> [depth] [kinds]
   q.py <graph.json> stats"""
import json,sys,collections
g=json.load(open(sys.argv[1])); N={n['id']:n for n in g['nodes']}
inn=collections.defaultdict(list); out=collections.defaultdict(list)
for e in g['edges']:
    inn[e['target']].append(e); out[e['source']].append(e)
def line(n):
    s=n.get('source','') ; p=s.split(':'); return int(p[1]) if len(p)>1 and p[1].isdigit() else -1
def show(n): return f"{n['id']} {n['type']} '{n.get('label','')}' @{':'.join(n.get('source','').split(':')[1:])}"
cmd=sys.argv[2]
if cmd=='stats':
    print(len(N),'nodes',len(g['edges']),'edges'); print(collections.Counter(n['type'] for n in g['nodes'])); print(collections.Counter(e['kind'] for e in g['edges']))
elif cmd=='nodes':
    for a in sys.argv[3:]:
        for n in g['nodes']:
            if line(n)==int(a): print(show(n), ' in:',[(e['source'],e['kind']) for e in inn[n['id']]],' out:',[(e['target'],e['kind']) for e in out[n['id']]])
elif cmd in('back','fwd'):
    start=sys.argv[3]; depth=int(sys.argv[4]) if len(sys.argv)>4 else 6
    kinds=set(sys.argv[5].split(',')) if len(sys.argv)>5 else None
    seen=set()
    def walk(i,d,via):
        mark='' if i not in seen else ' (seen)'
        print('  '*d+(f'<{via}> ' if via else '')+show(N[i])+mark)
        if i in seen or d>=depth: return
        seen.add(i)
        es=inn[i] if cmd=='back' else out[i]
        for e in es:
            if kinds and e['kind'] not in kinds: continue
            walk(e['source'] if cmd=='back' else e['target'],d+1,e['kind'])
    walk(start,0,None)
