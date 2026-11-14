# externalEnumSwitch: spike value graph

```mermaid
flowchart TD
  n0["App:22:13 CALL<br/>int fromStatement = 0"]
  n1["App:22:13 IDENTIFIER<br/>fromStatement"]
  n2["App:22:29 LITERAL<br/>0"]
  n3["App:23:17 CALL<br/>rounding()"]
  n4["App:23:17 IDENTIFIER<br/>Config"]
  n5["App:24:18 IDENTIFIER<br/>UP"]
  n6["App:25:17 CALL<br/>fromStatement = 1"]
  n7["App:25:17 IDENTIFIER<br/>fromStatement"]
  n8["App:25:33 LITERAL<br/>1"]
  n9["App:26:17 CONTROL_STRUCTURE<br/>break;"]
  n10["App:28:17 CALL<br/>fromStatement = 2"]
  n11["App:28:17 IDENTIFIER<br/>fromStatement"]
  n12["App:28:33 LITERAL<br/>2"]
  n13["App:30:13 CALL<br/>int fromExpression = switch(Config.rounding()) { case DOWN -#gt; 3; defau"]
  n14["App:30:13 IDENTIFIER<br/>fromExpression"]
  n15["App:30:30 CONTROL_STRUCTURE<br/>switch(Config.rounding())"]
  n16["App:30:38 CALL<br/>rounding()"]
  n17["App:30:38 IDENTIFIER<br/>Config"]
  n18["App:31:18 IDENTIFIER<br/>DOWN"]
  n19["App:32:24 LITERAL<br/>4"]
  n20["App:34:13 CALL<br/>int out = fromStatement + fromExpression"]
  n21["App:34:13 IDENTIFIER<br/>out"]
  n22["App:34:19 CALL<br/>fromStatement + fromExpression"]
  n23["App:34:19 IDENTIFIER<br/>fromStatement"]
  n24["App:34:35 IDENTIFIER<br/>fromExpression"]
  n0 -->|"local: ="| n1
  n10 -->|"local: ="| n11
  n11 -->|"local: fromStatement"| n23
  n12 -->|"local: 2"| n10
  n13 -->|"local: ="| n14
  n14 -->|"local: fromExpression"| n24
  n15 -->|"local: switch(Config.rounding())"| n13
  n16 -->|"guard: guard rounding()"| n18
  n16 -->|"guard: guard rounding()"| n19
  n17 -->|"local: Config"| n16
  n2 -->|"local: 0"| n0
  n20 -->|"local: ="| n21
  n22 -->|"local: fromStatement + fromExpression"| n20
  n23 -->|"local: fromStatement"| n22
  n24 -->|"local: fromExpression"| n22
  n3 -->|"guard: guard rounding()"| n10
  n3 -->|"guard: guard rounding()"| n11
  n3 -->|"guard: guard rounding()"| n12
  n3 -->|"guard: guard rounding()"| n5
  n3 -->|"guard: guard rounding()"| n6
  n3 -->|"guard: guard rounding()"| n7
  n3 -->|"guard: guard rounding()"| n8
  n3 -->|"guard: guard rounding()"| n9
  n4 -->|"local: Config"| n17
  n4 -->|"local: Config"| n3
  n6 -->|"local: ="| n7
  n7 -->|"local: fromStatement"| n23
  n8 -->|"local: 1"| n6
```
