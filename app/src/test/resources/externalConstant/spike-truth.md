# externalConstant: spike value graph

```mermaid
flowchart TD
  n0["App:21:13 CALL<br/>int budget = MAX_RETRIES"]
  n1["App:21:13 IDENTIFIER<br/>budget"]
  n2["App:21:22 IDENTIFIER<br/>MAX_RETRIES"]
  n3["App:22:13 CALL<br/>int spent = budget + 1"]
  n4["App:22:13 IDENTIFIER<br/>spent"]
  n5["App:22:21 CALL<br/>budget + 1"]
  n6["App:22:21 IDENTIFIER<br/>budget"]
  n7["App:22:30 LITERAL<br/>1"]
  n0 -->|"local: ="| n1
  n1 -->|"local: budget"| n6
  n2 -->|"local: MAX_RETRIES"| n0
  n3 -->|"local: ="| n4
  n5 -->|"local: budget + 1"| n3
  n6 -->|"local: budget"| n5
  n7 -->|"local: 1"| n5
```
