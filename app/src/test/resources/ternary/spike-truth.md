# ternary: spike value graph

```mermaid
flowchart TD
  n0["App:13:19 CALL<br/>int divisor = 0"]
  n1["App:13:19 IDENTIFIER<br/>divisor"]
  n2["App:13:29 LITERAL<br/>0"]
  n3["App:14:19 CALL<br/>int value = 100"]
  n4["App:14:19 IDENTIFIER<br/>value"]
  n5["App:14:27 LITERAL<br/>100"]
  n6["App:15:19 CALL<br/>int fallback = 7"]
  n7["App:15:19 IDENTIFIER<br/>fallback"]
  n8["App:15:30 LITERAL<br/>7"]
  n9["App:16:19 CALL<br/>int guarded = divisor == 0 ? fallback : value / divisor"]
  n10["App:16:19 IDENTIFIER<br/>guarded"]
  n11["App:16:29 CALL<br/>divisor == 0"]
  n12["App:16:29 CALL<br/>divisor == 0 ? fallback : value / divisor"]
  n13["App:16:29 IDENTIFIER<br/>divisor"]
  n14["App:16:40 LITERAL<br/>0"]
  n15["App:16:44 IDENTIFIER<br/>fallback"]
  n16["App:16:55 CALL<br/>value / divisor"]
  n17["App:16:55 IDENTIFIER<br/>value"]
  n18["App:16:63 IDENTIFIER<br/>divisor"]
  n0 -->|"local: ="| n1
  n1 -->|"local: divisor"| n13
  n11 -->|"guard: guard divisor == 0"| n15
  n11 -->|"guard: guard divisor == 0"| n16
  n11 -->|"guard: guard divisor == 0"| n17
  n11 -->|"guard: guard divisor == 0"| n18
  n11 -->|"local: divisor == 0"| n12
  n12 -->|"local: divisor == 0 ? fallback : value / divisor"| n9
  n13 -->|"local: divisor"| n11
  n13 -->|"local: divisor"| n18
  n14 -->|"local: 0"| n11
  n15 -->|"local: fallback"| n12
  n16 -->|"local: value / divisor"| n12
  n17 -->|"local: value"| n16
  n18 -->|"local: divisor"| n16
  n2 -->|"local: 0"| n0
  n3 -->|"local: ="| n4
  n4 -->|"local: value"| n17
  n5 -->|"local: 100"| n3
  n6 -->|"local: ="| n7
  n7 -->|"local: fallback"| n15
  n8 -->|"local: 7"| n6
  n9 -->|"local: ="| n10
```
