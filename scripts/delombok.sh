#!/usr/bin/env bash
#
# Rewrite a Lombok-annotated source tree into plain Java, so codeflow can read it.
#
# codeflow parses with `-proc:none` and no classpath, so a Lombok-generated member does not exist in
# the attributed tree: `@Getter` accessors, `@RequiredArgsConstructor` constructors and `@Slf4j`'s
# `log` field are all names javac cannot resolve, and every call to one is drawn as an opaque
# EXTERNAL box. Running Lombok *inside* codeflow would mean a jar dependency, `--add-opens` on the
# analysing JVM, and letting a processor discovered in the corpus execute during what the reader
# thinks is a read. `delombok` is Lombok's own answer: it writes the generated members out as
# ordinary source, which is codeflow's input contract with nothing added.
#
# Measured on fineract-progressive-loan (81 files): EXTERNAL 365 -> 308, with the drop accounted for
# exactly by getFromDate (31), getDueDate (23), mc (2) and loanProductRelatedDetail (1) reaching
# zero. See docs/externals.md, Finding 4 - including what it costs.
#
# The trade is positions. Every `file:line` codeflow prints afterwards - failure messages, node
# provenance - names the delomboked copy, not the file the reader has open.
#
#   scripts/delombok.sh <source-dir> [output-dir]
#
#   LOMBOK_JAR   the lombok jar to use; searched for in ~/.gradle and ~/.m2 when unset
#   JAVA_HOME    the JDK to run delombok on; `java` from PATH when unset
#
set -euo pipefail

if [ $# -lt 1 ]; then
    echo "usage: $0 <source-dir> [output-dir]" >&2
    exit 2
fi

src=$1
out=${2:-${TMPDIR:-/tmp}/delomboked}
java_bin=${JAVA_HOME:+$JAVA_HOME/bin/}java

# Both halves of the pattern are load-bearing. `-name 'lombok-*.jar'` alone matches things that are
# not Lombok at all - Kotlin ships a `lombok-compiler-plugin-for-ide` jar with no main class - and
# `-path '*projectlombok*'` alone matches the sources and javadoc jars beside the real one.
if [ -z "${LOMBOK_JAR:-}" ]; then
    LOMBOK_JAR=$(find ~/.gradle/caches ~/.m2 -path '*projectlombok*' -name 'lombok-[0-9]*.jar' \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' 2>/dev/null | sort -V | tail -1)
fi
if [ -z "$LOMBOK_JAR" ]; then
    echo "$0: no lombok jar found under ~/.gradle or ~/.m2; set LOMBOK_JAR" >&2
    exit 1
fi

# Lombok rewrites javac's own trees, so it needs the compiler's internals opened to it. Required
# from JDK 16 on, and the reason this is a separate program rather than something codeflow does:
# these flags would have to reach the analysing JVM, and the installed CLI's start script takes
# whatever JAVA_HOME names.
opens=()
for pkg in code comp file main model parser processing tree util jvm; do
    opens+=("--add-opens" "jdk.compiler/com.sun.tools.javac.$pkg=ALL-UNNAMED")
done

echo "$0: $LOMBOK_JAR -> $out" >&2

# Unresolved-symbol errors on stderr are expected and are not fatal: delombok is pointed at one
# module of a multi-module build, exactly as codeflow is, and writes every file it could read.
"$java_bin" "${opens[@]}" -jar "$LOMBOK_JAR" delombok "$src" -d "$out"

# delombok exits 0 having written nothing in more than one case - a source directory that is not
# there, an --add-opens set wrong for this JDK - so the count is the only thing that catches it. The
# `-d` test is not decoration: without it `find` fails on the missing directory and `pipefail` ends
# the script here, which is this guard being bypassed by the case it exists for.
written=0
[ -d "$out" ] && written=$(find "$out" -name '*.java' | wc -l | tr -d ' ')
if [ "$written" -eq 0 ]; then
    echo "$0: delombok wrote no files, having exited 0 - check that '$src' exists and that this" >&2
    echo "$0: JDK ($("$java_bin" -version 2>&1 | head -1)) is one this lombok jar supports" >&2
    exit 1
fi

echo "$0: $written files" >&2
echo "$0: ./gradlew run --args=\"$out --from Class#method\"" >&2
