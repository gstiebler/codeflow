# externalObject: spike value graph

```mermaid
flowchart TD
  n0["App:15:28 CALL<br/>List items = List.of()"]
  n1["App:15:28 IDENTIFIER<br/>items"]
  n2["App:15:36 CALL<br/>of()"]
  n3["App:15:36 IDENTIFIER<br/>List"]
  n4["App:16:19 CALL<br/>int size = items.size()"]
  n5["App:16:19 IDENTIFIER<br/>size"]
  n6["App:16:26 CALL<br/>size()"]
  n7["App:16:26 IDENTIFIER<br/>items"]
  n8["App:17:19 CALL<br/>int doubled = size + size"]
  n9["App:17:19 IDENTIFIER<br/>doubled"]
  n10["App:17:29 CALL<br/>size + size"]
  n11["App:17:29 IDENTIFIER<br/>size"]
  n12["App:17:36 IDENTIFIER<br/>size"]
  n0 -->|"local: ="| n1
  n1 -->|"local: items"| n7
  n10 -->|"local: size + size"| n8
  n11 -->|"local: size"| n10
  n12 -->|"local: size"| n10
  n2 -->|"local: of()"| n0
  n3 -->|"local: List"| n2
  n4 -->|"local: ="| n5
  n5 -->|"local: size"| n11
  n5 -->|"local: size"| n12
  n6 -->|"local: size()"| n4
  n7 -->|"local: items"| n6
  n8 -->|"local: ="| n9
```
