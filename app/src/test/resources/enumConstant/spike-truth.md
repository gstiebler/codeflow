# enumConstant: spike value graph

```mermaid
flowchart TD
  n0["App:13 CALL<br/>boolean big = true"]
  n1["App:13 IDENTIFIER<br/>big"]
  n2["App:13 LITERAL<br/>true"]
  n3["App:14 CALL<br/>Size chosen = Size.fromFlag(big)"]
  n4["App:14 CALL<br/>Size.fromFlag(big)"]
  n5["App:14 IDENTIFIER<br/>big"]
  n6["App:14 IDENTIFIER<br/>chosen"]
  n7["App:6 METHOD_PARAMETER_IN<br/>boolean big"]
  n8["App:7 CALL<br/>Size.SMALL"]
  n9["App:7 CALL<br/>big ? LARGE : Size.SMALL"]
  n10["App:7 FIELD_IDENTIFIER<br/>SMALL"]
  n11["App:7 IDENTIFIER<br/>LARGE"]
  n12["App:7 IDENTIFIER<br/>Size"]
  n13["App:7 IDENTIFIER<br/>big"]
  n14["App:7 RETURN<br/>return big ? LARGE : Size.SMALL;"]
  n0 -->|"local: ="| n1
  n1 -->|"local: big"| n5
  n10 -->|"local: SMALL"| n8
  n11 -->|"local: LARGE"| n9
  n12 -->|"local: Size"| n8
  n13 -->|"guard: guard big"| n10
  n13 -->|"guard: guard big"| n11
  n13 -->|"guard: guard big"| n12
  n13 -->|"guard: guard big"| n8
  n13 -->|"local: big"| n9
  n14 -->|"ret: return of fromFlag()"| n4
  n2 -->|"local: true"| n0
  n3 -->|"local: ="| n6
  n4 -->|"local: fromFlag()"| n3
  n5 -->|"arg: arg 1 big"| n7
  n8 -->|"local: field Size.SMALL"| n9
  n9 -->|"local: big ? LARGE : Size.SMALL"| n14
```
