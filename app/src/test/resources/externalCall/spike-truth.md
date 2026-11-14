# externalCall: spike value graph

```mermaid
flowchart TD
  n0["App:12:19 CALL<br/>int a = 5"]
  n1["App:12:19 IDENTIFIER<br/>a"]
  n2["App:12:23 LITERAL<br/>5"]
  n3["App:13:19 CALL<br/>int b = Math.abs(a)"]
  n4["App:13:19 IDENTIFIER<br/>b"]
  n5["App:13:23 CALL<br/>abs(a)"]
  n6["App:13:23 IDENTIFIER<br/>Math"]
  n7["App:13:32 IDENTIFIER<br/>a"]
  n8["App:14:16 FIELD_IDENTIFIER<br/>out"]
  n9["App:14:28 IDENTIFIER<br/>b"]
  n10["App:14:9 CALL<br/>System.out"]
  n11["App:14:9 CALL<br/>System.out.println(b)"]
  n12["App:14:9 IDENTIFIER<br/>System"]
  n13["App:15:19 CALL<br/>int c = b + 1"]
  n14["App:15:19 IDENTIFIER<br/>c"]
  n15["App:15:23 CALL<br/>b + 1"]
  n16["App:15:23 IDENTIFIER<br/>b"]
  n17["App:15:27 LITERAL<br/>1"]
  n0 -->|"local: ="| n1
  n1 -->|"local: a"| n7
  n10 -->|"local: field ANY.out"| n11
  n12 -->|"local: System"| n10
  n13 -->|"local: ="| n14
  n15 -->|"local: b + 1"| n13
  n16 -->|"local: b"| n15
  n17 -->|"local: 1"| n15
  n2 -->|"local: 5"| n0
  n3 -->|"local: ="| n4
  n4 -->|"local: b"| n9
  n5 -->|"local: abs()"| n3
  n6 -->|"local: Math"| n5
  n7 -->|"local: a"| n5
  n8 -->|"local: out"| n10
  n9 -->|"local: b"| n11
  n9 -->|"local: b"| n16
```
