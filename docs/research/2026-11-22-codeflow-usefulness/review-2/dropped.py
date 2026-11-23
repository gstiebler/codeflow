#!/usr/bin/env python3
"""dropped.py <graph.json> <source.java>
Call sites drawn with nothing but the event flowing in although the receiver is itself a call:
the receiver chain and its arguments were dropped."""
import collections, json, sys

g = json.load(open(sys.argv[1]))
src = open(sys.argv[2], encoding="utf-8").read().split("\n")
inn = collections.defaultdict(list)
for e in g["edges"]:
    inn[e["target"]].append(e)
sites = collections.defaultdict(list)
for n in g["nodes"]:
    p = n.get("source", "").split(":")
    if n["type"] == "EXTERNAL" and len(p) >= 3 and p[1].isdigit():
        sites[(int(p[1]), int(p[2]), n["label"])].append(n)
dropped = []
for (line, col, label), nodes in sorted(sites.items()):
    if any(e["kind"] != "EVENT" for n in nodes for e in inn[n["id"]]):
        continue
    text = src[line - 1][col - 1:] + " " + " ".join(s.strip() for s in src[line:line + 3])
    i = text.find("." + label + "(")
    if i > 0 and "(" in text[:i]:
        dropped.append((line, text[: i + len(label) + 2]))
print(f"{len(dropped)} of {len(sites)} external call sites lost their receiver chain")
for line, text in dropped:
    print(f"  {line}: {text[:120]}")
