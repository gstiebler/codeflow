# sameName: spike value graph

```mermaid
flowchart TD
  n0["App:12 METHOD_PARAMETER_IN<br/>this"]
  n1["App:13 CALL<br/>amount - 1"]
  n2["App:13 CALL<br/>int settled = amount - 1"]
  n3["App:13 CALL<br/>this.amount"]
  n4["App:13 FIELD_IDENTIFIER<br/>amount"]
  n5["App:13 IDENTIFIER<br/>settled"]
  n6["App:13 IDENTIFIER<br/>this"]
  n7["App:13 LITERAL<br/>1"]
  n8["App:14 IDENTIFIER<br/>settled"]
  n9["App:14 RETURN<br/>return settled;"]
  n10["App:18 METHOD_PARAMETER_IN<br/>this"]
  n11["App:21 METHOD_PARAMETER_IN<br/>this"]
  n12["App:22 CALL<br/>amount + 2"]
  n13["App:22 CALL<br/>int flushed = amount + 2"]
  n14["App:22 CALL<br/>this.amount"]
  n15["App:22 FIELD_IDENTIFIER<br/>amount"]
  n16["App:22 IDENTIFIER<br/>flushed"]
  n17["App:22 IDENTIFIER<br/>this"]
  n18["App:22 LITERAL<br/>2"]
  n19["App:23 IDENTIFIER<br/>flushed"]
  n20["App:23 RETURN<br/>return flushed;"]
  n21["App:29 CALL<br/>Account account = new Account()"]
  n22["App:29 CALL<br/>new Account()"]
  n23["App:29 IDENTIFIER<br/>account"]
  n24["App:30 CALL<br/>account.amount"]
  n25["App:30 CALL<br/>account.amount = 10"]
  n26["App:30 FIELD_IDENTIFIER<br/>amount"]
  n27["App:30 IDENTIFIER<br/>account"]
  n28["App:30 LITERAL<br/>10"]
  n29["App:31 CALL<br/>account.close()"]
  n30["App:31 CALL<br/>int fromAccount = account.close()"]
  n31["App:31 IDENTIFIER<br/>account"]
  n32["App:31 IDENTIFIER<br/>fromAccount"]
  n33["App:33 CALL<br/>Connection connection = new Connection()"]
  n34["App:33 CALL<br/>new Connection()"]
  n35["App:33 IDENTIFIER<br/>connection"]
  n36["App:34 CALL<br/>connection.amount"]
  n37["App:34 CALL<br/>connection.amount = 20"]
  n38["App:34 FIELD_IDENTIFIER<br/>amount"]
  n39["App:34 IDENTIFIER<br/>connection"]
  n40["App:34 LITERAL<br/>20"]
  n41["App:35 CALL<br/>connection.close()"]
  n42["App:35 CALL<br/>int fromConnection = connection.close()"]
  n43["App:35 IDENTIFIER<br/>connection"]
  n44["App:35 IDENTIFIER<br/>fromConnection"]
  n45["App:9 METHOD_PARAMETER_IN<br/>this"]
  n0 -->|"local: this"| n3
  n0 -->|"local: this"| n6
  n1 -->|"local: amount - 1"| n2
  n11 -->|"local: this"| n14
  n11 -->|"local: this"| n17
  n12 -->|"local: amount + 2"| n13
  n13 -->|"local: ="| n16
  n14 -->|"local: field Connection.amount"| n12
  n15 -->|"local: amount"| n14
  n16 -->|"local: flushed"| n19
  n17 -->|"local: this"| n14
  n18 -->|"local: 2"| n12
  n19 -->|"local: flushed"| n20
  n2 -->|"local: ="| n5
  n20 -->|"ret: return of close()"| n41
  n21 -->|"local: ="| n23
  n22 -->|"local: new Account()"| n21
  n23 -->|"arg: arg 0 this"| n45
  n23 -->|"local: account"| n22
  n23 -->|"local: account"| n23
  n23 -->|"local: account"| n27
  n23 -->|"local: account"| n31
  n24 -->|"field: field Account.amount"| n3
  n24 -->|"local: field Account.amount"| n31
  n25 -->|"local: ="| n24
  n26 -->|"local: amount"| n24
  n27 -->|"local: account"| n24
  n28 -->|"local: 10"| n25
  n29 -->|"local: close()"| n30
  n3 -->|"local: field Account.amount"| n1
  n30 -->|"local: ="| n32
  n31 -->|"arg: arg 0 this"| n0
  n33 -->|"local: ="| n35
  n34 -->|"local: new Connection()"| n33
  n35 -->|"arg: arg 0 this"| n10
  n35 -->|"local: connection"| n34
  n35 -->|"local: connection"| n35
  n35 -->|"local: connection"| n39
  n35 -->|"local: connection"| n43
  n36 -->|"field: field Connection.amount"| n14
  n36 -->|"local: field Connection.amount"| n43
  n37 -->|"local: ="| n36
  n38 -->|"local: amount"| n36
  n39 -->|"local: connection"| n36
  n4 -->|"local: amount"| n3
  n40 -->|"local: 20"| n37
  n41 -->|"local: close()"| n42
  n42 -->|"local: ="| n44
  n43 -->|"arg: arg 0 this"| n11
  n5 -->|"local: settled"| n8
  n6 -->|"local: this"| n3
  n7 -->|"local: 1"| n1
  n8 -->|"local: settled"| n9
  n9 -->|"ret: return of close()"| n29
```
