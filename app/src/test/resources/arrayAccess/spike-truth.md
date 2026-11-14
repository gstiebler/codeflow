# arrayAccess: spike value graph

```mermaid
flowchart TD
  n0["App:4 METHOD_PARAMETER_IN<br/>String[] args"]
  n1["App:5 CALL<br/>int position = 1"]
  n2["App:5 IDENTIFIER<br/>position"]
  n3["App:5 LITERAL<br/>1"]
  n4["App:6 CALL<br/>String picked = args[position]"]
  n5["App:6 CALL<br/>args[position]"]
  n6["App:6 IDENTIFIER<br/>args"]
  n7["App:6 IDENTIFIER<br/>picked"]
  n8["App:6 IDENTIFIER<br/>position"]
  n9["App:7 CALL<br/>int size = picked.length()"]
  n10["App:7 CALL<br/>length()"]
  n11["App:7 IDENTIFIER<br/>picked"]
  n12["App:7 IDENTIFIER<br/>size"]
  n0 -->|"local: args"| n5
  n0 -->|"local: args"| n6
  n1 -->|"local: ="| n2
  n10 -->|"local: length()"| n9
  n11 -->|"local: picked"| n10
  n3 -->|"local: 1"| n1
  n4 -->|"local: ="| n7
  n5 -->|"local: args[position]"| n4
  n6 -->|"local: args"| n5
  n7 -->|"local: picked"| n11
  n8 -->|"local: position"| n5
  n9 -->|"local: ="| n12
```
