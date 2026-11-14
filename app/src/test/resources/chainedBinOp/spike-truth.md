# chainedBinOp: spike value graph

```mermaid
flowchart TD
  n0["App:10:19 CALL<br/>int a = 10"]
  n1["App:10:19 IDENTIFIER<br/>a"]
  n2["App:10:23 LITERAL<br/>10"]
  n3["App:11:19 CALL<br/>int b = 2"]
  n4["App:11:19 IDENTIFIER<br/>b"]
  n5["App:11:23 LITERAL<br/>2"]
  n6["App:12:19 CALL<br/>int c = 3"]
  n7["App:12:19 IDENTIFIER<br/>c"]
  n8["App:12:23 LITERAL<br/>3"]
  n9["App:13:19 CALL<br/>int chained = a + b + c"]
  n10["App:13:19 IDENTIFIER<br/>chained"]
  n11["App:13:29 CALL<br/>a + b"]
  n12["App:13:29 CALL<br/>a + b + c"]
  n13["App:13:29 IDENTIFIER<br/>a"]
  n14["App:13:33 IDENTIFIER<br/>b"]
  n15["App:13:37 IDENTIFIER<br/>c"]
  n16["App:14:19 CALL<br/>int nested = a - b - c"]
  n17["App:14:19 IDENTIFIER<br/>nested"]
  n18["App:14:28 CALL<br/>a - b"]
  n19["App:14:28 CALL<br/>a - b - c"]
  n20["App:14:28 IDENTIFIER<br/>a"]
  n21["App:14:32 IDENTIFIER<br/>b"]
  n22["App:14:36 IDENTIFIER<br/>c"]
  n0 -->|"local: ="| n1
  n1 -->|"local: a"| n13
  n11 -->|"local: a + b"| n12
  n12 -->|"local: a + b + c"| n9
  n13 -->|"local: a"| n11
  n13 -->|"local: a"| n20
  n14 -->|"local: b"| n11
  n14 -->|"local: b"| n21
  n15 -->|"local: c"| n12
  n15 -->|"local: c"| n22
  n16 -->|"local: ="| n17
  n18 -->|"local: a - b"| n19
  n19 -->|"local: a - b - c"| n16
  n2 -->|"local: 10"| n0
  n20 -->|"local: a"| n18
  n21 -->|"local: b"| n18
  n22 -->|"local: c"| n19
  n3 -->|"local: ="| n4
  n4 -->|"local: b"| n14
  n5 -->|"local: 2"| n3
  n6 -->|"local: ="| n7
  n7 -->|"local: c"| n15
  n8 -->|"local: 3"| n6
  n9 -->|"local: ="| n10
```
