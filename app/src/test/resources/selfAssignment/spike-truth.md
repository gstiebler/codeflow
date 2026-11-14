# selfAssignment: spike value graph

```mermaid
flowchart TD
  n0["App:15:13 CALL<br/>int total = 1"]
  n1["App:15:13 IDENTIFIER<br/>total"]
  n2["App:15:21 LITERAL<br/>1"]
  n3["App:16:17 CALL<br/>total + 10"]
  n4["App:16:17 IDENTIFIER<br/>total"]
  n5["App:16:25 LITERAL<br/>10"]
  n6["App:16:9 CALL<br/>total = total + 10"]
  n7["App:16:9 IDENTIFIER<br/>total"]
  n8["App:17:19 CALL<br/>int expanded = total"]
  n9["App:17:19 IDENTIFIER<br/>expanded"]
  n10["App:17:30 IDENTIFIER<br/>total"]
  n11["App:19:13 CALL<br/>int folded = 2"]
  n12["App:19:13 IDENTIFIER<br/>folded"]
  n13["App:19:22 LITERAL<br/>2"]
  n14["App:20:19 LITERAL<br/>20"]
  n15["App:20:9 CALL<br/>folded += 20"]
  n16["App:20:9 IDENTIFIER<br/>folded"]
  n17["App:21:19 CALL<br/>int compound = folded"]
  n18["App:21:19 IDENTIFIER<br/>compound"]
  n19["App:21:30 IDENTIFIER<br/>folded"]
  n20["App:23 CALL<br/>new Counter()"]
  n21["App:23:23 CALL<br/>Counter counter = new Counter()"]
  n22["App:23:23 IDENTIFIER #2<br/>counter"]
  n23["App:23:23 IDENTIFIER<br/>counter"]
  n24["App:23:33 CALL<br/>new Counter()"]
  n25["App:24:17 FIELD_IDENTIFIER<br/>count"]
  n26["App:24:25 LITERAL<br/>3"]
  n27["App:24:9 CALL<br/>counter.count"]
  n28["App:24:9 CALL<br/>counter.count = 3"]
  n29["App:24:9 IDENTIFIER<br/>counter"]
  n30["App:25:17 FIELD_IDENTIFIER<br/>count"]
  n31["App:25:25 CALL<br/>counter.count"]
  n32["App:25:25 CALL<br/>counter.count + 30"]
  n33["App:25:25 IDENTIFIER<br/>counter"]
  n34["App:25:33 FIELD_IDENTIFIER<br/>count"]
  n35["App:25:41 LITERAL<br/>30"]
  n36["App:25:9 CALL<br/>counter.count"]
  n37["App:25:9 CALL<br/>counter.count = counter.count + 30"]
  n38["App:25:9 IDENTIFIER<br/>counter"]
  n39["App:26:19 CALL<br/>int field = counter.count"]
  n40["App:26:19 IDENTIFIER<br/>field"]
  n41["App:26:27 CALL<br/>counter.count"]
  n42["App:26:27 IDENTIFIER<br/>counter"]
  n43["App:26:35 FIELD_IDENTIFIER<br/>count"]
  n44["App:9:1 METHOD_PARAMETER_IN<br/>this"]
  n0 -->|"local: ="| n1
  n1 -->|"local: total"| n4
  n10 -->|"local: total"| n8
  n11 -->|"local: ="| n12
  n12 -->|"local: folded"| n16
  n13 -->|"local: 2"| n11
  n14 -->|"local: 20"| n15
  n16 -->|"local: folded"| n15
  n16 -->|"local: folded"| n19
  n17 -->|"local: ="| n18
  n19 -->|"local: folded"| n17
  n2 -->|"local: 1"| n0
  n21 -->|"local: ="| n23
  n22 -->|"arg: arg 0 this"| n44
  n22 -->|"local: counter"| n20
  n22 -->|"local: counter"| n29
  n22 -->|"local: counter"| n31
  n22 -->|"local: counter"| n33
  n22 -->|"local: counter"| n38
  n22 -->|"local: counter"| n41
  n22 -->|"local: counter"| n42
  n23 -->|"local: counter"| n22
  n24 -->|"local: new Counter()"| n21
  n25 -->|"local: count"| n27
  n26 -->|"local: 3"| n28
  n27 -->|"field: field Counter.count"| n31
  n27 -->|"field: field Counter.count"| n41
  n27 -->|"local: field Counter.count"| n31
  n27 -->|"local: field Counter.count"| n33
  n27 -->|"local: field Counter.count"| n38
  n28 -->|"local: ="| n27
  n29 -->|"local: counter"| n27
  n3 -->|"local: total + 10"| n6
  n30 -->|"local: count"| n36
  n31 -->|"local: field Counter.count"| n32
  n32 -->|"local: counter.count + 30"| n37
  n33 -->|"local: counter"| n31
  n34 -->|"local: count"| n31
  n35 -->|"local: 30"| n32
  n36 -->|"field: field Counter.count"| n31
  n36 -->|"field: field Counter.count"| n41
  n36 -->|"local: field Counter.count"| n41
  n36 -->|"local: field Counter.count"| n42
  n37 -->|"local: ="| n36
  n38 -->|"local: counter"| n36
  n39 -->|"local: ="| n40
  n4 -->|"local: total"| n3
  n41 -->|"local: field Counter.count"| n39
  n42 -->|"local: counter"| n41
  n43 -->|"local: count"| n41
  n5 -->|"local: 10"| n3
  n6 -->|"local: ="| n7
  n7 -->|"local: total"| n10
  n8 -->|"local: ="| n9
```
