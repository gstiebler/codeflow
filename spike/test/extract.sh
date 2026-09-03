#!/usr/bin/env bash
# Build a CPG from spike/fixtures/tiny, dump it, and check Joern produces the shape the hand-written
# fixture claims. Not part of `npm run test:spike` - it needs Joern and takes minutes.
#
#   spike/test/extract.sh [out-dir]
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
out="${1:-$root/out/tiny}"
mkdir -p "$out"

"$root/build-cpg.sh" "$root/fixtures/tiny" "$out/tiny.cpg"
"$root/dump.sh" "$out/tiny.cpg" "$out/tiny.json" tiny-fixture
node "$here/extract-check.ts" "$out/tiny.json"
