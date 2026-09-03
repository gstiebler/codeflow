A whole Java program in six files, declaring its own annotations so `javasrc2cpg` needs no jars on
the classpath. It is the smallest thing that exercises every boundary rule and every edge kind: an
HTTP endpoint, a repository read and a repository write, a value crossing a call and coming back
through a return, a value crossing a field with no call between writer and reader, an exception the
JDK throws with no `throw` in the source, and a two-statement cycle.

It is the same program `spike/test/corpus.ts` describes by hand. That is the point: the unit tests
run against the hand-written version in milliseconds, and `spike/test/extract.sh` checks that Joern
produces the same shape from real source.
