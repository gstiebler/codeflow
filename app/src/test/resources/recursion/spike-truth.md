# recursion: spike value graph

```mermaid
flowchart TD
  n0["App:13 CALL<br/>int seed = 5"]
  n1["App:13 IDENTIFIER<br/>seed"]
  n2["App:13 LITERAL<br/>5"]
  n3["App:14 CALL<br/>fact(seed)"]
  n4["App:14 CALL<br/>int out = fact(seed)"]
  n5["App:14 IDENTIFIER<br/>out"]
  n6["App:14 IDENTIFIER<br/>seed"]
  n7["App:5 METHOD_PARAMETER_IN<br/>int n"]
  n8["App:6 CALL<br/>n == 0"]
  n9["App:6 IDENTIFIER<br/>n"]
  n10["App:6 LITERAL<br/>0"]
  n11["App:7 LITERAL<br/>1"]
  n12["App:7 RETURN<br/>return 1;"]
  n13["App:9 CALL<br/>fact(n - 1)"]
  n14["App:9 CALL<br/>n * fact(n - 1)"]
  n15["App:9 CALL<br/>n - 1"]
  n16["App:9 IDENTIFIER<br/>n"]
  n17["App:9 LITERAL<br/>1"]
  n18["App:9 RETURN<br/>return n * fact(n - 1);"]
  n0 -->|"local: ="| n1
  n1 -->|"local: seed"| n6
  n10 -->|"local: 0"| n8
  n11 -->|"local: 1"| n12
  n12 -->|"ret: return of fact()"| n13
  n12 -->|"ret: return of fact()"| n3
  n13 -->|"local: fact()"| n14
  n14 -->|"local: n * fact(n - 1)"| n18
  n15 -->|"arg: arg 1 n"| n7
  n16 -->|"local: n"| n14
  n16 -->|"local: n"| n15
  n16 -->|"local: n"| n16
  n17 -->|"local: 1"| n15
  n18 -->|"ret: return of fact()"| n13
  n18 -->|"ret: return of fact()"| n3
  n2 -->|"local: 5"| n0
  n3 -->|"local: fact()"| n4
  n4 -->|"local: ="| n5
  n6 -->|"arg: arg 1 n"| n7
  n7 -->|"local: n"| n9
  n8 -->|"guard: guard n == 0"| n11
  n8 -->|"guard: guard n == 0"| n12
  n8 -->|"guard: guard n == 0"| n13
  n8 -->|"guard: guard n == 0"| n14
  n8 -->|"guard: guard n == 0"| n15
  n8 -->|"guard: guard n == 0"| n16
  n8 -->|"guard: guard n == 0"| n17
  n8 -->|"guard: guard n == 0"| n18
  n9 -->|"local: n"| n16
  n9 -->|"local: n"| n8
```
