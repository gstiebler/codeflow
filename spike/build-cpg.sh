#!/usr/bin/env bash
# Build a CPG from a Java source tree.
#
#   spike/build-cpg.sh <src-dir> <out.cpg>
#
# --delombok-mode no-delombok is not optional. Joern's default and types-only modes deadlock on
# Fineract modules: it shells out to `lombok.launch.Main delombok`, the child writes its files, and
# then neither process returns - a full stderr pipe from the cross-module "cannot find symbol"
# errors. The same delombok run by hand finishes in eight seconds, so the hang is Joern's subprocess
# handling. And even once that is fixed, do not turn it on: the default mode reports line numbers
# from the delomboked copy, not from the source. Measured on this repo, source line 85 came back as
# 71, 427 as 354, 252 as 205. Every line number the spike prints would be wrong.
#
# What this costs: Lombok-generated getters and setters do not exist in the CPG at all. They appear
# as calls whose methodFullName starts <unresolvedNamespace>, which is why every rule in
# boundaries.ts matches on the receiver's *simple* type name.
set -euo pipefail

JOERN_HOME="${JOERN_HOME:-/opt/homebrew/Cellar/joern/4.0.570/libexec}"
src="${1:?usage: build-cpg.sh <src-dir> <out.cpg>}"
cpg="${2:?usage: build-cpg.sh <src-dir> <out.cpg>}"

"$JOERN_HOME/javasrc2cpg" --delombok-mode no-delombok --output "$cpg" "$src"
