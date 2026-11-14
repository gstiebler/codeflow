#!/usr/bin/env bash
# Build a CPG from spike/fixtures/callbacks, dump it to fixtures/callbacks/graph.json, assert every
# callback flow is followed, then compare the generated Mermaid with fixtures/callbacks/truth.md.
# Not part of `npm run test:spike` (it needs Joern); the snapshot comparison alone is, through
# callbacks-truth.test.ts. After a Joern or export change, refresh graph.json and truth.md with
#
#   UPDATE_SNAPSHOTS=1 spike/test/extract-callbacks.sh [out-dir]
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
out="${1:-$root/out/callbacks}"
mkdir -p "$out"

"$root/build-cpg.sh" "$root/fixtures/callbacks" "$out/callbacks.cpg"
"$root/dump.sh" "$out/callbacks.cpg" "$out/callbacks.json" callbacks-fixture
cp "$out/callbacks.json" "$root/fixtures/callbacks/graph.json"
node "$here/extract-callbacks-check.ts" "$root/fixtures/callbacks/graph.json"
node --test "$here/callbacks-truth.test.ts"
