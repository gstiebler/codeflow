#!/usr/bin/env bash
# Stand-in for the `java` executable, passed to Joern as --delombok-java-home (Joern uses that value
# as the path to the java binary, not a JDK dir). javasrc2cpg launches the same
# `java -cp @argfile lombok.launch.Main delombok <src>/. -d <tmp>` once PER SOURCE FILE, all in
# parallel (hundreds of identical JVMs) -> memory exhaustion. This wrapper runs delombok once and
# turns every duplicate launch into a no-op. DELOMBOK_JAVA picks the real java (Java 17 recommended
# by Joern). delombok may print "cannot find symbol" for cross-module refs and exit non-zero but
# still writes its output, so the exit code is ignored.
set -uo pipefail

out=""
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  [[ "${args[i]}" == "-d" ]] && out="${args[i + 1]:-}"
done
[[ -n "$out" ]] || { echo "delombok-java.sh: no -d <dir> in args" >&2; exit 2; }

until mkdir "$out.lock" 2>/dev/null; do sleep 0.2; done
trap 'rmdir "$out.lock"' EXIT

[[ -e "$out/.delombok-done" ]] && exit 0

"${DELOMBOK_JAVA:-java}" -Xmx1g "$@" </dev/null >"$out.log" 2>&1
mkdir -p "$out"
touch "$out/.delombok-done"
exit 0
