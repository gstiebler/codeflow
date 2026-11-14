# tryCatch: spike value graph

```mermaid
flowchart TD
  n0["App:16:29 METHOD_PARAMETER_IN<br/>String[] args"]
  n1["App:17:13 CALL<br/>int attempts = 1"]
  n2["App:17:13 IDENTIFIER<br/>attempts"]
  n3["App:17:24 LITERAL<br/>1"]
  n4["App:20:13 CALL<br/>result = Integer.parseInt(args[0])"]
  n5["App:20:13 IDENTIFIER<br/>result"]
  n6["App:20:22 CALL<br/>parseInt(args[0])"]
  n7["App:20:22 IDENTIFIER<br/>Integer"]
  n8["App:20:39 CALL<br/>args[0]"]
  n9["App:20:39 IDENTIFIER<br/>args"]
  n10["App:20:44 LITERAL<br/>0"]
  n11["App:22:13 CALL<br/>result = attempts"]
  n12["App:22:13 IDENTIFIER<br/>result"]
  n13["App:22:22 IDENTIFIER<br/>attempts"]
  n14["App:24:13 CALL<br/>int reported = result"]
  n15["App:24:13 IDENTIFIER<br/>reported"]
  n16["App:24:24 IDENTIFIER<br/>result"]
  n0 -->|"local: args"| n8
  n0 -->|"local: args"| n9
  n1 -->|"local: ="| n2
  n10 -->|"local: 0"| n8
  n11 -->|"local: ="| n12
  n12 -->|"local: result"| n16
  n13 -->|"local: attempts"| n11
  n14 -->|"local: ="| n15
  n16 -->|"local: result"| n14
  n2 -->|"local: attempts"| n13
  n3 -->|"local: 1"| n1
  n4 -->|"guard: guard result = Integer.parseInt(args[0])"| n11
  n4 -->|"guard: guard result = Integer.parseInt(args[0])"| n12
  n4 -->|"guard: guard result = Integer.parseInt(args[0])"| n13
  n4 -->|"local: ="| n5
  n5 -->|"local: result"| n16
  n6 -->|"local: parseInt()"| n4
  n7 -->|"local: Integer"| n6
  n8 -->|"local: args[0]"| n6
  n9 -->|"local: args"| n8
```
