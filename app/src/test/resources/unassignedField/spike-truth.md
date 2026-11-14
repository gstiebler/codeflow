# unassignedField: spike value graph

```mermaid
flowchart TD
  n0["App:17:11 METHOD_PARAMETER_IN<br/>Builder builder"]
  n1["App:17:5 METHOD_PARAMETER_IN<br/>this"]
  n2["App:18:22 CALL<br/>builder.filled"]
  n3["App:18:22 IDENTIFIER<br/>builder"]
  n4["App:18:30 FIELD_IDENTIFIER<br/>filled"]
  n5["App:18:9 CALL<br/>this.fromFilled"]
  n6["App:18:9 CALL<br/>this.fromFilled = builder.filled"]
  n7["App:18:9 FIELD_IDENTIFIER<br/>fromFilled"]
  n8["App:18:9 IDENTIFIER<br/>this"]
  n9["App:19:24 CALL<br/>builder.neverSet"]
  n10["App:19:24 IDENTIFIER<br/>builder"]
  n11["App:19:32 FIELD_IDENTIFIER<br/>neverSet"]
  n12["App:19:9 CALL<br/>this.fromNeverSet"]
  n13["App:19:9 CALL<br/>this.fromNeverSet = builder.neverSet"]
  n14["App:19:9 FIELD_IDENTIFIER<br/>fromNeverSet"]
  n15["App:19:9 IDENTIFIER<br/>this"]
  n16["App:25 CALL<br/>new Builder()"]
  n17["App:25:17 CALL<br/>Builder builder = new Builder()"]
  n18["App:25:17 IDENTIFIER #2<br/>builder"]
  n19["App:25:17 IDENTIFIER<br/>builder"]
  n20["App:25:27 CALL<br/>new Builder()"]
  n21["App:26 CALL<br/>new Built(builder.fill(4))"]
  n22["App:26:15 CALL<br/>Built built = new Built(builder.fill(4))"]
  n23["App:26:15 IDENTIFIER #2<br/>built"]
  n24["App:26:15 IDENTIFIER<br/>built"]
  n25["App:26:23 CALL<br/>new Built(builder.fill(4))"]
  n26["App:26:33 CALL<br/>builder.fill(4)"]
  n27["App:26:33 IDENTIFIER<br/>builder"]
  n28["App:26:46 LITERAL<br/>4"]
  n29["App:27:13 CALL<br/>int out = built.fromFilled"]
  n30["App:27:13 IDENTIFIER<br/>out"]
  n31["App:27:19 CALL<br/>built.fromFilled"]
  n32["App:27:19 IDENTIFIER<br/>built"]
  n33["App:27:25 FIELD_IDENTIFIER<br/>fromFilled"]
  n34["App:3:1 METHOD_PARAMETER_IN<br/>this"]
  n35["App:7:18 METHOD_PARAMETER_IN<br/>int seed"]
  n36["App:7:5 METHOD_PARAMETER_IN<br/>this"]
  n37["App:8:18 IDENTIFIER<br/>seed"]
  n38["App:8:9 CALL<br/>this.filled"]
  n39["App:8:9 CALL<br/>this.filled = seed"]
  n40["App:8:9 FIELD_IDENTIFIER<br/>filled"]
  n41["App:8:9 IDENTIFIER<br/>this"]
  n42["App:9:16 IDENTIFIER<br/>this"]
  n43["App:9:9 RETURN<br/>return this;"]
  n0 -->|"local: builder"| n10
  n0 -->|"local: builder"| n2
  n0 -->|"local: builder"| n3
  n0 -->|"local: builder"| n9
  n1 -->|"local: this"| n15
  n1 -->|"local: this"| n8
  n10 -->|"local: builder"| n9
  n11 -->|"local: neverSet"| n9
  n13 -->|"local: ="| n12
  n14 -->|"local: fromNeverSet"| n12
  n15 -->|"local: this"| n12
  n17 -->|"local: ="| n19
  n18 -->|"arg: arg 0 this"| n34
  n18 -->|"local: builder"| n16
  n18 -->|"local: builder"| n27
  n19 -->|"local: builder"| n18
  n2 -->|"local: field Builder.filled"| n10
  n2 -->|"local: field Builder.filled"| n6
  n20 -->|"local: new Builder()"| n17
  n22 -->|"local: ="| n24
  n23 -->|"arg: arg 0 this"| n1
  n23 -->|"local: built"| n21
  n23 -->|"local: built"| n31
  n23 -->|"local: built"| n32
  n24 -->|"local: built"| n23
  n25 -->|"local: new Built(builder.fill(4))"| n22
  n26 -->|"arg: arg 1 builder"| n0
  n26 -->|"local: fill()"| n21
  n27 -->|"arg: arg 0 this"| n36
  n28 -->|"arg: arg 1 seed"| n35
  n29 -->|"local: ="| n30
  n3 -->|"local: builder"| n2
  n31 -->|"local: field Built.fromFilled"| n29
  n32 -->|"local: built"| n31
  n33 -->|"local: fromFilled"| n31
  n35 -->|"local: seed"| n37
  n36 -->|"local: this"| n41
  n36 -->|"local: this"| n42
  n37 -->|"local: seed"| n39
  n38 -->|"field: field Builder.filled"| n2
  n38 -->|"local: field Builder.filled"| n42
  n39 -->|"local: ="| n38
  n4 -->|"local: filled"| n2
  n40 -->|"local: filled"| n38
  n41 -->|"local: this"| n38
  n42 -->|"local: this"| n43
  n43 -->|"ret: return of fill()"| n26
  n5 -->|"field: field Built.fromFilled"| n31
  n5 -->|"local: field Built.fromFilled"| n15
  n6 -->|"local: ="| n5
  n7 -->|"local: fromFilled"| n5
  n8 -->|"local: this"| n5
  n9 -->|"local: field Builder.neverSet"| n13
```
