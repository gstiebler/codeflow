# unary: spike value graph

```mermaid
flowchart TD
  n0["App:12:19 CALL<br/>int value = 7"]
  n1["App:12:19 IDENTIFIER<br/>value"]
  n2["App:12:27 LITERAL<br/>7"]
  n3["App:13:23 CALL<br/>boolean flag = true"]
  n4["App:13:23 IDENTIFIER<br/>flag"]
  n5["App:13:30 LITERAL<br/>true"]
  n6["App:15:19 CALL<br/>int negated = -value"]
  n7["App:15:19 IDENTIFIER<br/>negated"]
  n8["App:15:29 CALL<br/>-value"]
  n9["App:15:30 IDENTIFIER<br/>value"]
  n10["App:16:23 CALL<br/>boolean inverted = !flag"]
  n11["App:16:23 IDENTIFIER<br/>inverted"]
  n12["App:16:34 CALL<br/>!flag"]
  n13["App:16:35 IDENTIFIER<br/>flag"]
  n14["App:18:13 CALL<br/>int counter = 0"]
  n15["App:18:13 IDENTIFIER<br/>counter"]
  n16["App:18:23 LITERAL<br/>0"]
  n17["App:19:9 CALL<br/>counter++"]
  n18["App:19:9 IDENTIFIER<br/>counter"]
  n19["App:20:19 CALL<br/>int afterIncrement = counter"]
  n20["App:20:19 IDENTIFIER<br/>afterIncrement"]
  n21["App:20:36 IDENTIFIER<br/>counter"]
  n0 -->|"local: ="| n1
  n1 -->|"local: value"| n9
  n10 -->|"local: ="| n11
  n12 -->|"local: !flag"| n10
  n13 -->|"local: flag"| n12
  n14 -->|"local: ="| n15
  n15 -->|"local: counter"| n18
  n16 -->|"local: 0"| n14
  n18 -->|"local: counter"| n17
  n18 -->|"local: counter"| n21
  n19 -->|"local: ="| n20
  n2 -->|"local: 7"| n0
  n21 -->|"local: counter"| n19
  n3 -->|"local: ="| n4
  n4 -->|"local: flag"| n13
  n5 -->|"local: true"| n3
  n6 -->|"local: ="| n7
  n8 -->|"local: -value"| n6
  n9 -->|"local: value"| n8
```
