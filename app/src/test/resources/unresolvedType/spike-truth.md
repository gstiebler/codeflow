# unresolvedType: spike value graph

```mermaid
flowchart TD
  n0["App:15:13 CALL<br/>int size = 5"]
  n1["App:15:13 IDENTIFIER<br/>size"]
  n2["App:15:20 LITERAL<br/>5"]
  n3["App:16:16 CALL<br/>Widget widget = Widget.create(size)"]
  n4["App:16:16 IDENTIFIER<br/>widget"]
  n5["App:16:25 CALL<br/>create(size)"]
  n6["App:16:25 IDENTIFIER<br/>Widget"]
  n7["App:16:39 IDENTIFIER<br/>size"]
  n8["App:17:13 CALL<br/>int measured = widget.measure()"]
  n9["App:17:13 IDENTIFIER<br/>measured"]
  n10["App:17:24 CALL<br/>measure()"]
  n11["App:17:24 IDENTIFIER<br/>widget"]
  n12["App:18:13 CALL<br/>int total = measured + 1"]
  n13["App:18:13 IDENTIFIER<br/>total"]
  n14["App:18:21 CALL<br/>measured + 1"]
  n15["App:18:21 IDENTIFIER<br/>measured"]
  n16["App:18:32 LITERAL<br/>1"]
  n0 -->|"local: ="| n1
  n1 -->|"local: size"| n7
  n10 -->|"local: measure()"| n8
  n11 -->|"local: widget"| n10
  n12 -->|"local: ="| n13
  n14 -->|"local: measured + 1"| n12
  n15 -->|"local: measured"| n14
  n16 -->|"local: 1"| n14
  n2 -->|"local: 5"| n0
  n3 -->|"local: ="| n4
  n4 -->|"local: widget"| n11
  n5 -->|"local: create()"| n3
  n6 -->|"local: Widget"| n5
  n7 -->|"local: size"| n5
  n8 -->|"local: ="| n9
  n9 -->|"local: measured"| n15
```
