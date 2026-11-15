#!/usr/bin/env bash
# Graph every method of a real corpus, each as its own root, in one run, and fail if codeflow
# crashed or skipped a root. Then summarise every method (`--summaries`), and fail if that crashed
# or wrote something that is not JSON. The fixtures certify behaviour on code written for them;
# this is the check on code nobody wrote for codeflow, which is where its zero-byte failures have
# come from (javac 21's `yield` bug, one `import static`, one `(int)` cast).
#
#   scripts/smoke.sh <codeflow-binary> <java-source-dir> [label]
#
# Unmodelled constructs make codeflow exit 1 with the document written in full, so the exit status
# cannot tell them from a crash. What decides here is a stack trace on stderr, an empty document,
# or fewer roots graphed than asked for. The counters are reported, not judged - summary gaps
# included, which are counted the way the unmodelled constructs are.
set -euo pipefail

bin=$1
src=$2
label=${3:-$src}
err=$(mktemp)
summaries=$(mktemp)
summaries_err=$(mktemp)
trap 'rm -f "$err" "$summaries" "$summaries_err"' EXIT

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

# Every method's summary, in a run of its own: no --from, since it analyses every method anyway.
start=$(date +%s)
summaries_status=0
"$bin" "$src" --summaries >"$summaries" 2>"$summaries_err" || summaries_status=$?
summaries_seconds=$(( $(date +%s) - start ))
summaries_bytes=$(wc -c <"$summaries" | tr -d ' ')
# Parsing is the check that the document is JSON; the gap counts by kind come out of the same load.
summaries_json=0
summaries_gaps=$(python3 -c 'import json,sys; print(json.load(sys.stdin)["counts"]["gaps"])' <"$summaries" 2>/dev/null) \
  || summaries_json=$?

report() {
  echo "### codeflow smoke: $label"
  echo
  echo "- roots: $graphed of $count graphed, in ${seconds}s"
  echo "- document: $bytes bytes, exit $status"
  grep -E '^codeflow: [0-9]+ (of [0-9]+ references unresolved|constructs? not modelled)' "$err" | sed 's/^/- /' || true
  local unmodelled
  unmodelled=$(grep -E '^codeflow:   ' "$err" | sed -E 's/^codeflow: +//; s/ at .*//' | sort | uniq -c | sort -rn || true)
  [ -z "$unmodelled" ] || { echo; echo '```'; echo "$unmodelled"; echo '```'; }
  echo
  echo "- summaries: $summaries_bytes bytes, in ${summaries_seconds}s, exit $summaries_status"
  grep -E '^codeflow: summarised ' "$summaries_err" | sed 's/^/- /' || true
  [ -z "$summaries_gaps" ] || echo "- gaps by kind: $summaries_gaps"
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
if grep -qE '^(Exception in thread|Caused by:)' "$summaries_err"; then
  echo; echo "smoke: codeflow --summaries crashed:"; grep -E -A 12 '^Exception in thread' "$summaries_err" | head -40
  failed=1
fi
[ "$summaries_json" -eq 0 ] || { echo "smoke: the --summaries document is not JSON"; failed=1; }
exit $failed
