# generic: spike value graph

```mermaid
flowchart TD
  n0["App:10:12 METHOD_PARAMETER_IN<br/>T initial"]
  n1["App:10:5 METHOD_PARAMETER_IN<br/>this"]
  n2["App:11:16 IDENTIFIER<br/>initial"]
  n3["App:11:9 CALL<br/>this.held"]
  n4["App:11:9 CALL<br/>this.held = initial"]
  n5["App:11:9 FIELD_IDENTIFIER<br/>held"]
  n6["App:11:9 IDENTIFIER<br/>this"]
  n7["App:14:5 METHOD_PARAMETER_IN<br/>this"]
  n8["App:15:16 CALL<br/>this.held"]
  n9["App:15:16 FIELD_IDENTIFIER<br/>held"]
  n10["App:15:16 IDENTIFIER<br/>this"]
  n11["App:15:9 RETURN<br/>return held;"]
  n12["App:21 CALL<br/>new Holder#lt;#gt;(#quot;payload#quot;)"]
  n13["App:21:30 CALL<br/>Holder holder = new Holder#lt;#gt;(#quot;payload#quot;)"]
  n14["App:21:30 IDENTIFIER #2<br/>holder"]
  n15["App:21:30 IDENTIFIER<br/>holder"]
  n16["App:21:39 CALL<br/>new Holder#lt;#gt;(#quot;payload#quot;)"]
  n17["App:21:52 LITERAL<br/>#quot;payload#quot;"]
  n18["App:22:22 CALL<br/>String taken = holder.get()"]
  n19["App:22:22 IDENTIFIER<br/>taken"]
  n20["App:22:30 CALL<br/>get()"]
  n21["App:22:30 IDENTIFIER<br/>holder"]
  n22["App:23:19 CALL<br/>int size = taken.length()"]
  n23["App:23:19 IDENTIFIER<br/>size"]
  n24["App:23:26 CALL<br/>length()"]
  n25["App:23:26 IDENTIFIER<br/>taken"]
  n0 -->|"local: initial"| n2
  n1 -->|"local: this"| n6
  n10 -->|"local: this"| n8
  n13 -->|"local: ="| n15
  n14 -->|"local: holder"| n12
  n14 -->|"local: holder"| n21
  n15 -->|"local: holder"| n14
  n16 -->|"local: new Holder#lt;#gt;(#quot;payload#quot;)"| n13
  n17 -->|"local: #quot;payload#quot;"| n12
  n18 -->|"local: ="| n19
  n19 -->|"local: taken"| n25
  n2 -->|"local: initial"| n4
  n20 -->|"local: get()"| n18
  n21 -->|"local: holder"| n20
  n22 -->|"local: ="| n23
  n24 -->|"local: length()"| n22
  n25 -->|"local: taken"| n24
  n3 -->|"field: field Holder.held"| n8
  n4 -->|"local: ="| n3
  n5 -->|"local: held"| n3
  n6 -->|"local: this"| n3
  n7 -->|"local: this"| n10
  n7 -->|"local: this"| n8
  n8 -->|"local: field Holder.held"| n11
  n9 -->|"local: held"| n8
```
