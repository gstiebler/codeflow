#!/usr/bin/env bash
# Graph every method of a real corpus, each as its own root, in one run, and fail if codeflow
# crashed or skipped a root. The fixtures certify behaviour on code written for them; this is the
# check on code nobody wrote for codeflow, which is where its zero-byte failures have come from
# (javac 21's `yield` bug, one `import static`, one `(int)` cast).
#
#   scripts/smoke.sh <codeflow-binary> <java-source-dir> [label]
#
# Unmodelled constructs make codeflow exit 1 with the document written in full, so the exit status
# cannot tell them from a crash. What decides here is a stack trace on stderr, an empty document,
# or fewer roots graphed than asked for. The counters are reported, not judged.
set -euo pipefail

bin=$1
src=$2
label=${3:-$src}
err=$(mktemp)
trap 'rm -f "$err"' EXIT

# An unknown --from lists every method the sources declare, which is the candidate set codeflow
# itself would accept, overloads and constructors included.
roots=$( ("$bin" "$src" --from 'NoSuchClass#noSuchMethod' 2>&1 >/dev/null || true) \
  | sed -n 's/.*Name one with --from Class#method: //p' | tr ',' '\n' | sed 's/^ *//' | grep . | sort -u || true)
count=$(grep -c . <<<"$roots" || true)
[ "$count" -gt 0 ] || { echo "smoke: no candidate methods found in $src"; exit 1; }

args=()
while read -r root; do args+=(--from "$root"); done <<<"$roots"

start=$(date +%s)
status=0
bytes=$("$bin" "$src" --json "${args[@]}" 2>"$err" | wc -c | tr -d ' ') || status=$?
seconds=$(( $(date +%s) - start ))
graphed=$(grep -c '^codeflow: graphing ' "$err" || true)

report() {
  echo "### codeflow smoke: $label"
  echo
  echo "- roots: $graphed of $count graphed, in ${seconds}s"
  echo "- document: $bytes bytes, exit $status"
  grep -E '^codeflow: [0-9]+ (of [0-9]+ references unresolved|constructs? not modelled)' "$err" | sed 's/^/- /' || true
  local unmodelled
  unmodelled=$(grep -E '^codeflow:   ' "$err" | sed -E 's/^codeflow: +//; s/ at .*//' | sort | uniq -c | sort -rn || true)
  [ -z "$unmodelled" ] || { echo; echo '```'; echo "$unmodelled"; echo '```'; }
}
report
[ -n "${GITHUB_STEP_SUMMARY:-}" ] && report >>"$GITHUB_STEP_SUMMARY"

failed=0
if grep -qE '^(Exception in thread|Caused by:)' "$err"; then
  echo; echo "smoke: codeflow crashed:"; grep -E -A 12 '^Exception in thread' "$err" | head -40
  failed=1
fi
[ "$bytes" -gt 0 ] || { echo "smoke: empty document"; failed=1; }
[ "$graphed" -eq "$count" ] || { echo "smoke: graphed $graphed of $count roots"; failed=1; }
exit $failed
