# objectArgument: spike value graph

```mermaid
flowchart TD
  n0["App:12:21 METHOD_PARAMETER_IN<br/>Source origin"]
  n1["App:13:16 CALL<br/>origin.value"]
  n2["App:13:16 IDENTIFIER<br/>origin"]
  n3["App:13:23 FIELD_IDENTIFIER<br/>value"]
  n4["App:13:9 RETURN<br/>return origin.value;"]
  n5["App:19 CALL<br/>new Source(7)"]
  n6["App:19:16 CALL<br/>Source source = new Source(7)"]
  n7["App:19:16 IDENTIFIER #2<br/>source"]
  n8["App:19:16 IDENTIFIER<br/>source"]
  n9["App:19:25 CALL<br/>new Source(7)"]
  n10["App:19:36 LITERAL<br/>7"]
  n11["App:20:13 CALL<br/>int out = Reader.read(source)"]
  n12["App:20:13 IDENTIFIER<br/>out"]
  n13["App:20:19 CALL<br/>Reader.read(source)"]
  n14["App:20:31 IDENTIFIER<br/>source"]
  n15["App:6:12 METHOD_PARAMETER_IN<br/>int seed"]
  n16["App:6:5 METHOD_PARAMETER_IN<br/>this"]
  n17["App:7:17 IDENTIFIER<br/>seed"]
  n18["App:7:9 CALL<br/>this.value"]
  n19["App:7:9 CALL<br/>this.value = seed"]
  n20["App:7:9 FIELD_IDENTIFIER<br/>value"]
  n21["App:7:9 IDENTIFIER<br/>this"]
  n0 -->|"local: origin"| n1
  n0 -->|"local: origin"| n2
  n1 -->|"local: field Source.value"| n4
  n10 -->|"arg: arg 1 seed"| n15
  n10 -->|"local: 7"| n5
  n11 -->|"local: ="| n12
  n13 -->|"local: read()"| n11
  n14 -->|"arg: arg 1 origin"| n0
  n15 -->|"local: seed"| n17
  n16 -->|"local: this"| n21
  n17 -->|"local: seed"| n19
  n18 -->|"field: field Source.value"| n1
  n19 -->|"local: ="| n18
  n2 -->|"local: origin"| n1
  n20 -->|"local: value"| n18
  n21 -->|"local: this"| n18
  n3 -->|"local: value"| n1
  n4 -->|"ret: return of read()"| n13
  n6 -->|"local: ="| n8
  n7 -->|"arg: arg 0 this"| n16
  n7 -->|"local: source"| n14
  n7 -->|"local: source"| n5
  n8 -->|"local: source"| n7
  n9 -->|"local: new Source(7)"| n6
```
