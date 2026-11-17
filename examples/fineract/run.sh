#!/usr/bin/env bash
# Graph Fineract entry points: one HTML page per *.args file in this directory.
#
#   examples/fineract/run.sh <fineract-checkout> <out-dir> [name.args ...]
#
# Needs `./gradlew installDist` first. Builds a source dir of symlinks to every
# fineract-*/src/main/java (client, e2e, avro, doc and war modules left out) plus
# stubs/ (Spring Data interfaces, so save/findById resolve and can be boundaries),
# then runs codeflow once per arguments file into <out-dir>/<name>.html.
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
app="$here/../../app/build/install/app/bin/app"
fineract="$(cd "$1" && pwd)"
out="$(mkdir -p "$2" && cd "$2" && pwd)"
shift 2

src="$out/src"
rm -rf "$src"
mkdir -p "$src"
for module in "$fineract"/fineract-*/; do
  name="$(basename "$module")"
  case "$name" in fineract-client*|fineract-e2e*|fineract-avro*|fineract-doc|fineract-war) continue ;; esac
  [ -d "$module/src/main/java" ] && ln -s "$module/src/main/java" "$src/$name"
done
ln -s "$here/stubs" "$src/stubs"

files=("$@")
[ ${#files[@]} -eq 0 ] && files=("$here"/*.args)

export JAVA_OPTS="${JAVA_OPTS:--Xmx8g}"
for args in "${files[@]}"; do
  [ -f "$args" ] || args="$here/$args"   # a bare name means one of ours
  name="$(basename "$args" .args)"
  echo "== $name" >&2
  # Non-zero also means "some construct not modelled" (listed on stderr); the page is still written.
  "$app" "$src" "@$args" --html > "$out/$name.html" || echo "== $name: exit $?" >&2
done
