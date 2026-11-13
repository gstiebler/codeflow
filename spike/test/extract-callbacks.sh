#!/usr/bin/env bash
# Build a CPG from spike/fixtures/callbacks, dump it, and report which callback flows are followed.
# Not part of `npm run test:spike` - it needs Joern and takes minutes.
#
#   spike/test/extract-callbacks.sh [out-dir]
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
out="${1:-$root/out/callbacks}"
mkdir -p "$out"

"$root/build-cpg.sh" "$root/fixtures/callbacks" "$out/callbacks.cpg"
"$root/dump.sh" "$out/callbacks.cpg" "$out/callbacks.json" callbacks-fixture
node "$here/extract-callbacks-check.ts" "$out/callbacks.json"
