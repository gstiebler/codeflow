# enumConstructor: spike value graph

```mermaid
flowchart TD
  n0["App:22:10 METHOD_PARAMETER_IN<br/>int units"]
  n1["App:22:5 METHOD_PARAMETER_IN<br/>this"]
  n2["App:23:14 FIELD_IDENTIFIER<br/>units"]
  n3["App:23:22 IDENTIFIER<br/>units"]
  n4["App:23:9 CALL<br/>this.units"]
  n5["App:23:9 CALL<br/>this.units = units"]
  n6["App:23:9 IDENTIFIER<br/>this"]
  n7["App:26:5 METHOD_PARAMETER_IN<br/>this"]
  n8["App:27:16 CALL<br/>this.units"]
  n9["App:27:16 FIELD_IDENTIFIER<br/>units"]
  n10["App:27:16 IDENTIFIER<br/>this"]
  n11["App:27:9 RETURN<br/>return units;"]
  n12["App:33:13 CALL<br/>int small = Size.SMALL.units()"]
  n13["App:33:13 IDENTIFIER<br/>small"]
  n14["App:33:21 CALL<br/>Size.SMALL"]
  n15["App:33:21 CALL<br/>Size.SMALL.units()"]
  n16["App:33:21 IDENTIFIER<br/>Size"]
  n17["App:33:26 FIELD_IDENTIFIER<br/>SMALL"]
  n18["App:34:13 CALL<br/>int large = Size.LARGE.units()"]
  n19["App:34:13 IDENTIFIER<br/>large"]
  n20["App:34:21 CALL<br/>Size.LARGE"]
  n21["App:34:21 CALL<br/>Size.LARGE.units()"]
  n22["App:34:21 IDENTIFIER<br/>Size"]
  n23["App:34:26 FIELD_IDENTIFIER<br/>LARGE"]
  n24["App:35:13 CALL<br/>int out = small + large"]
  n25["App:35:13 IDENTIFIER<br/>out"]
  n26["App:35:19 CALL<br/>small + large"]
  n27["App:35:19 IDENTIFIER<br/>small"]
  n28["App:35:27 IDENTIFIER<br/>large"]
  n0 -->|"local: units"| n3
  n1 -->|"local: this"| n6
  n10 -->|"local: this"| n8
  n11 -->|"ret: return of units()"| n15
  n11 -->|"ret: return of units()"| n21
  n12 -->|"local: ="| n13
  n13 -->|"local: small"| n27
  n14 -->|"arg: arg 0 this"| n7
  n14 -->|"local: field Size.SMALL"| n22
  n15 -->|"local: units()"| n12
  n16 -->|"local: Size"| n14
  n17 -->|"local: SMALL"| n14
  n18 -->|"local: ="| n19
  n19 -->|"local: large"| n28
  n2 -->|"local: units"| n4
  n20 -->|"arg: arg 0 this"| n7
  n21 -->|"local: units()"| n18
  n22 -->|"local: Size"| n20
  n23 -->|"local: LARGE"| n20
  n24 -->|"local: ="| n25
  n26 -->|"local: small + large"| n24
  n27 -->|"local: small"| n26
  n28 -->|"local: large"| n26
  n3 -->|"local: units"| n5
  n4 -->|"field: field Size.units"| n8
  n5 -->|"local: ="| n4
  n6 -->|"local: this"| n4
  n7 -->|"local: this"| n10
  n7 -->|"local: this"| n8
  n8 -->|"local: field Size.units"| n11
  n9 -->|"local: units"| n8
```
