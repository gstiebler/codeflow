#!/usr/bin/env bash
# Build a CPG from a Java source tree.
#
#   spike/build-cpg.sh <src-dir> <out.cpg>
#
# DELOMBOK_MODE=no-delombok (default) or types-only. Both analyse the original source, so line
# numbers match the files. Do not use `default` or `run-delombok`: they report line numbers from the
# delomboked copy (source line 85 came back as 71, 427 as 354).
#
# types-only resolves Lombok getters/setters' types, but javasrc2cpg launches the same delombok
# command once PER SOURCE FILE, all in parallel (482 JVMs on a 482-file module) -> memory exhaustion
# (this looked like a deadlock). delombok-java.sh, passed as --delombok-java-home (which Joern uses
# as the java executable), runs it once. Set DELOMBOK_JAVA to a Java 17 binary. On
# fineract-working-capital-loan: 25 s, calls with unknown class 25.5% -> 23.8%.
#
# What no-delombok costs: Lombok-generated getters and setters do not exist in the CPG at all. They appear
# as calls whose methodFullName starts <unresolvedNamespace>, which is why every rule in
# boundaries.ts matches on the receiver's *simple* type name.
set -euo pipefail

JOERN_HOME="${JOERN_HOME:-/opt/homebrew/Cellar/joern/4.0.570/libexec}"
src="${1:?usage: build-cpg.sh <src-dir> <out.cpg>}"
cpg="${2:?usage: build-cpg.sh <src-dir> <out.cpg>}"

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mode="${DELOMBOK_MODE:-no-delombok}"

case "$mode" in
  no-delombok) "$JOERN_HOME/javasrc2cpg" --delombok-mode no-delombok --output "$cpg" "$src" ;;
  types-only)
    "$JOERN_HOME/javasrc2cpg" -J-Xmx4g --delombok-mode types-only \
      --delombok-java-home "$here/delombok-java.sh" --output "$cpg" "$src" ;;
  *) echo "DELOMBOK_MODE must be no-delombok or types-only, got: $mode" >&2; exit 2 ;;
esac
