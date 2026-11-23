#!/usr/bin/env bash
# fetch.sh <owner/repo> <ref> <outdir> <path>...   -> raw file contents at ref into outdir (flat)
set -euo pipefail
repo=$1; ref=$2; out=$3; shift 3
mkdir -p "$out"
for p in "$@"; do
  gh api -H "Accept: application/vnd.github.raw" "repos/$repo/contents/$p?ref=$ref" > "$out/$(basename "$p")"
  echo "$(wc -l < "$out/$(basename "$p")") $out/$(basename "$p")"
done
