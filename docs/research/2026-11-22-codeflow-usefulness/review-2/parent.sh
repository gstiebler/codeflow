#!/usr/bin/env bash
# parent.sh <owner/repo> <pr>  -> "<merge> <parent1>"
set -euo pipefail
m=$(gh pr view "$2" -R "$1" --json mergeCommit -q .mergeCommit.oid)
p=$(gh api "repos/$1/commits/$m" -q '.parents[0].sha')
echo "$m $p"
