# catchParameter: spike value graph

```mermaid
flowchart TD
  n0["App:10 CALL<br/>args[0]"]
  n1["App:10 CALL<br/>out = Integer.parseInt(args[0])"]
  n2["App:10 CALL<br/>parseInt(args[0])"]
  n3["App:10 IDENTIFIER<br/>Integer"]
  n4["App:10 IDENTIFIER<br/>args"]
  n5["App:10 IDENTIFIER<br/>out"]
  n6["App:10 LITERAL<br/>0"]
  n7["App:12 CALL<br/>String reason = failure.getMessage()"]
  n8["App:12 CALL<br/>getMessage()"]
  n9["App:12 IDENTIFIER<br/>failure"]
  n10["App:12 IDENTIFIER<br/>reason"]
  n11["App:13 CALL<br/>length()"]
  n12["App:13 CALL<br/>out = reason.length()"]
  n13["App:13 IDENTIFIER<br/>out"]
  n14["App:13 IDENTIFIER<br/>reason"]
  n15["App:7 METHOD_PARAMETER_IN<br/>String[] args"]
  n16["App:8 CALL<br/>int out = 0"]
  n17["App:8 IDENTIFIER<br/>out"]
  n18["App:8 LITERAL<br/>0"]
  n0 -->|"local: args[0]"| n2
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n10
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n11
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n12
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n13
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n14
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n7
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n8
  n1 -->|"guard: guard out = Integer.parseInt(args[0])"| n9
  n1 -->|"local: ="| n5
  n10 -->|"local: reason"| n14
  n11 -->|"local: length()"| n12
  n12 -->|"local: ="| n13
  n14 -->|"local: reason"| n11
  n15 -->|"local: args"| n0
  n15 -->|"local: args"| n4
  n16 -->|"local: ="| n17
  n18 -->|"local: 0"| n16
  n2 -->|"local: parseInt()"| n1
  n3 -->|"local: Integer"| n2
  n4 -->|"local: args"| n0
  n6 -->|"local: 0"| n0
  n7 -->|"local: ="| n10
  n8 -->|"local: getMessage()"| n7
  n9 -->|"local: failure"| n8
```
