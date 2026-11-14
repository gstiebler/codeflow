#!/usr/bin/env bash
# Build one CPG per Kotlin-engine golden case (app/src/test/resources/<case>), dump it to
# <case>/spike-graph.json, then compare the generated Mermaid with <case>/spike-truth.md. One Joern
# run per case because the cases collide (many are `package test; class App`). Not part of
# `npm run test:spike` (it needs Joern); the snapshot comparison alone is, through
# cases-truth.test.ts. After a Joern or export change, refresh everything with
#
#   UPDATE_SNAPSHOTS=1 spike/test/extract-cases.sh [out-dir]
#
# Runs Joern sequentially, one process at a time (memory).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
res="$(dirname "$root")/app/src/test/resources"
out="${1:-$root/out/cases}"
mkdir -p "$out"

for dir in "$res"/*/; do
  name="$(basename "$dir")"
  # codemap is not a normal case: it holds a large out/ tree.
  [ "$name" = codemap ] && continue
  [ -n "$(find "$dir" -name '*.java' -print -quit)" ] || continue
  "$root/build-cpg.sh" "$dir" "$out/$name.cpg"
  "$root/dump.sh" "$out/$name.cpg" "$out/$name.json" "$name"
  cp "$out/$name.json" "$dir/spike-graph.json"
done

node --test "$here/cases-truth.test.ts"
