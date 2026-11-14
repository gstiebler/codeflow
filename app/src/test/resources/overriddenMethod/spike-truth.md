# overriddenMethod: spike value graph

```mermaid
flowchart TD
  n0["App:12 METHOD_PARAMETER_IN<br/>int x"]
  n1["App:12 METHOD_PARAMETER_IN<br/>this"]
  n2["App:13 CALL<br/>x + 1"]
  n3["App:13 IDENTIFIER<br/>x"]
  n4["App:13 LITERAL<br/>1"]
  n5["App:13 RETURN<br/>return x + 1;"]
  n6["App:17 METHOD_PARAMETER_IN<br/>this"]
  n7["App:19 METHOD_PARAMETER_IN<br/>int x"]
  n8["App:20 CALL<br/>x * 100"]
  n9["App:20 IDENTIFIER<br/>x"]
  n10["App:20 LITERAL<br/>100"]
  n11["App:20 RETURN<br/>return x * 100;"]
  n12["App:26 CALL<br/>Base b = new Sub()"]
  n13["App:26 CALL<br/>new Sub()"]
  n14["App:26 IDENTIFIER<br/>b"]
  n15["App:27 CALL<br/>b.f(7)"]
  n16["App:27 CALL<br/>int out = b.f(7)"]
  n17["App:27 IDENTIFIER<br/>b"]
  n18["App:27 IDENTIFIER<br/>out"]
  n19["App:27 LITERAL<br/>7"]
  n0 -->|"local: x"| n3
  n10 -->|"local: 100"| n8
  n12 -->|"local: ="| n14
  n13 -->|"local: new Sub()"| n12
  n14 -->|"arg: arg 0 this"| n6
  n14 -->|"local: b"| n13
  n14 -->|"local: b"| n14
  n14 -->|"local: b"| n17
  n15 -->|"local: f()"| n16
  n16 -->|"local: ="| n18
  n17 -->|"arg: arg 0 this"| n1
  n19 -->|"arg: arg 1 x"| n0
  n2 -->|"local: x + 1"| n5
  n3 -->|"local: x"| n2
  n4 -->|"local: 1"| n2
  n5 -->|"ret: return of f()"| n15
  n7 -->|"local: x"| n9
  n8 -->|"local: x * 100"| n11
  n9 -->|"local: x"| n8
```
