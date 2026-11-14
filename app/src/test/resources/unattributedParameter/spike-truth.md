# unattributedParameter: spike value graph

```mermaid
flowchart TD
  n0["App:20 CALL<br/>Long id = 5L"]
  n1["App:20 IDENTIFIER<br/>id"]
  n2["App:20 LITERAL<br/>5L"]
  n3["App:21 CALL<br/>Report report = new Report(Report.Reason.LOGGED, id)"]
  n4["App:21 CALL<br/>Report.Reason"]
  n5["App:21 CALL<br/>Report.Reason.LOGGED"]
  n6["App:21 CALL<br/>new Report(Report.Reason.LOGGED, id)"]
  n7["App:21 FIELD_IDENTIFIER<br/>LOGGED"]
  n8["App:21 FIELD_IDENTIFIER<br/>Reason"]
  n9["App:21 IDENTIFIER<br/>Report"]
  n10["App:21 IDENTIFIER<br/>id"]
  n11["App:21 IDENTIFIER<br/>report"]
  n12["Report:10 METHOD_PARAMETER_IN<br/>this"]
  n13["Report:11 CALL<br/>this.name()"]
  n14["Report:11 IDENTIFIER<br/>this"]
  n15["Report:11 RETURN<br/>return name();"]
  n16["Report:15 METHOD_PARAMETER_IN<br/>final Long id"]
  n17["Report:15 METHOD_PARAMETER_IN<br/>final Reason reason"]
  n18["Report:15 METHOD_PARAMETER_IN<br/>this"]
  n19["Report:16 CALL<br/>code()"]
  n20["Report:16 CALL<br/>super(reason.code(), id);"]
  n21["Report:16 IDENTIFIER<br/>id"]
  n22["Report:16 IDENTIFIER<br/>reason"]
  n23["Report:16 IDENTIFIER<br/>this"]
  n24["Report:7 CALL<br/>this.outerClass"]
  n25["Report:7 CALL<br/>this.outerClass = outerClass"]
  n26["Report:7 FIELD_IDENTIFIER<br/>outerClass"]
  n27["Report:7 IDENTIFIER<br/>outerClass"]
  n28["Report:7 IDENTIFIER<br/>this"]
  n29["Report:7 METHOD_PARAMETER_IN<br/>outerClass"]
  n30["Report:7 METHOD_PARAMETER_IN<br/>this"]
  n0 -->|"local: ="| n1
  n1 -->|"local: id"| n10
  n10 -->|"arg: arg 2 id"| n16
  n10 -->|"local: id"| n6
  n11 -->|"arg: arg 0 this"| n18
  n11 -->|"local: report"| n11
  n11 -->|"local: report"| n6
  n12 -->|"local: this"| n14
  n13 -->|"local: name()"| n15
  n14 -->|"local: this"| n13
  n15 -->|"ret: return of code()"| n19
  n16 -->|"local: id"| n21
  n17 -->|"local: reason"| n22
  n18 -->|"local: this"| n23
  n19 -->|"local: code()"| n20
  n2 -->|"local: 5L"| n0
  n21 -->|"local: id"| n20
  n22 -->|"arg: arg 0 this"| n12
  n23 -->|"local: this"| n20
  n25 -->|"local: ="| n24
  n26 -->|"local: outerClass"| n24
  n27 -->|"local: outerClass"| n25
  n28 -->|"local: this"| n24
  n29 -->|"local: outerClass"| n27
  n3 -->|"local: ="| n11
  n30 -->|"local: this"| n28
  n4 -->|"local: field Report.Reason"| n5
  n5 -->|"arg: arg 1 reason"| n17
  n5 -->|"local: field Reason.LOGGED"| n6
  n6 -->|"local: new Report(Report.Reason.LOGGED, id)"| n3
  n7 -->|"local: LOGGED"| n5
  n8 -->|"local: Reason"| n4
  n9 -->|"local: Report"| n4
```
