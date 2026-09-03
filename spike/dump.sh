#!/usr/bin/env bash
# Dump a CPG as the JSON spike/src/types.ts describes.
#
#   spike/dump.sh <in.cpg> <out.json> [commit]
#
# Budget about two and a half minutes, nearly all of it Joern applying its default overlays on
# import. The Scala compiles first, so a typo in export.sc costs the whole run.
set -euo pipefail

JOERN_HOME="${JOERN_HOME:-/opt/homebrew/Cellar/joern/4.0.570/libexec}"
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cpg="${1:?usage: dump.sh <in.cpg> <out.json> [commit]}"
out="${2:?usage: dump.sh <in.cpg> <out.json> [commit]}"
commit="${3:-unknown}"

# 10g because the default heap is killed (exit 137) part-way through a Fineract module.
"$JOERN_HOME/joern" -J-Xmx10g --script "$here/joern/export.sc" \
  --param "cpgFile=$(cd "$(dirname "$cpg")" && pwd)/$(basename "$cpg")" \
  --param "out=$out" \
  --param "commit=$commit"
