# base: spike value graph

```mermaid
flowchart TD
  n0["App:10 CALL<br/>b + 8"]
  n1["App:10 CALL<br/>int c = b + 8"]
  n2["App:10 IDENTIFIER<br/>b"]
  n3["App:10 IDENTIFIER<br/>c"]
  n4["App:10 LITERAL<br/>8"]
  n5["App:12 CALL<br/>d = b"]
  n6["App:12 IDENTIFIER<br/>b"]
  n7["App:12 IDENTIFIER<br/>d"]
  n8["App:13 CALL<br/>int e = d"]
  n9["App:13 IDENTIFIER<br/>d"]
  n10["App:13 IDENTIFIER<br/>e"]
  n11["App:8 CALL<br/>int a = 5"]
  n12["App:8 IDENTIFIER<br/>a"]
  n13["App:8 LITERAL<br/>5"]
  n14["App:9 CALL<br/>int b = a"]
  n15["App:9 IDENTIFIER<br/>a"]
  n16["App:9 IDENTIFIER<br/>b"]
  n0 -->|"local: b + 8"| n1
  n1 -->|"local: ="| n3
  n11 -->|"local: ="| n12
  n12 -->|"local: a"| n15
  n13 -->|"local: 5"| n11
  n14 -->|"local: ="| n16
  n15 -->|"local: a"| n14
  n16 -->|"local: b"| n2
  n2 -->|"local: b"| n0
  n2 -->|"local: b"| n6
  n4 -->|"local: 8"| n0
  n5 -->|"local: ="| n7
  n6 -->|"local: b"| n5
  n7 -->|"local: d"| n9
  n8 -->|"local: ="| n10
  n9 -->|"local: d"| n8
```
