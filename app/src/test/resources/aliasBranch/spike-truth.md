# aliasBranch: spike value graph

```mermaid
flowchart TD
  n0["App:19 METHOD_PARAMETER_IN<br/>int amount"]
  n1["App:19 METHOD_PARAMETER_IN<br/>this"]
  n2["App:20 CALL<br/>this.amount"]
  n3["App:20 CALL<br/>this.amount = amount"]
  n4["App:20 FIELD_IDENTIFIER<br/>amount"]
  n5["App:20 IDENTIFIER<br/>amount"]
  n6["App:20 IDENTIFIER<br/>this"]
  n7["App:26 CALL<br/>args.length"]
  n8["App:26 CALL<br/>args.length #gt; 0"]
  n9["App:26 IDENTIFIER<br/>args"]
  n10["App:26 LITERAL<br/>0"]
  n11["App:27 CALL<br/>chosen = new Holder(11)"]
  n12["App:27 CALL<br/>new Holder(11)"]
  n13["App:27 IDENTIFIER<br/>chosen"]
  n14["App:27 LITERAL<br/>11"]
  n15["App:29 CALL<br/>chosen = new Holder(22)"]
  n16["App:29 CALL<br/>new Holder(22)"]
  n17["App:29 IDENTIFIER<br/>chosen"]
  n18["App:29 LITERAL<br/>22"]
  n19["App:31 CALL<br/>chosen.amount"]
  n20["App:31 CALL<br/>int total = chosen.amount"]
  n21["App:31 FIELD_IDENTIFIER<br/>amount"]
  n22["App:31 IDENTIFIER<br/>chosen"]
  n23["App:31 IDENTIFIER<br/>total"]
  n0 -->|"local: amount"| n5
  n1 -->|"local: this"| n6
  n10 -->|"local: 0"| n8
  n11 -->|"local: ="| n13
  n12 -->|"local: new Holder(11)"| n11
  n13 -->|"arg: arg 0 this"| n1
  n13 -->|"local: chosen"| n12
  n13 -->|"local: chosen"| n13
  n13 -->|"local: chosen"| n19
  n13 -->|"local: chosen"| n22
  n14 -->|"arg: arg 1 amount"| n0
  n14 -->|"local: 11"| n12
  n15 -->|"local: ="| n17
  n16 -->|"local: new Holder(22)"| n15
  n17 -->|"arg: arg 0 this"| n1
  n17 -->|"local: chosen"| n16
  n17 -->|"local: chosen"| n17
  n17 -->|"local: chosen"| n19
  n17 -->|"local: chosen"| n22
  n18 -->|"arg: arg 1 amount"| n0
  n18 -->|"local: 22"| n16
  n19 -->|"local: field Holder.amount"| n20
  n2 -->|"field: field Holder.amount"| n19
  n20 -->|"local: ="| n23
  n21 -->|"local: amount"| n19
  n22 -->|"local: chosen"| n19
  n3 -->|"local: ="| n2
  n4 -->|"local: amount"| n2
  n5 -->|"local: amount"| n3
  n6 -->|"local: this"| n2
  n7 -->|"local: args.length"| n8
  n8 -->|"guard: guard args.length #gt; 0"| n11
  n8 -->|"guard: guard args.length #gt; 0"| n12
  n8 -->|"guard: guard args.length #gt; 0"| n13
  n8 -->|"guard: guard args.length #gt; 0"| n14
  n8 -->|"guard: guard args.length #gt; 0"| n15
  n8 -->|"guard: guard args.length #gt; 0"| n16
  n8 -->|"guard: guard args.length #gt; 0"| n17
  n8 -->|"guard: guard args.length #gt; 0"| n18
  n9 -->|"local: args"| n7
```
