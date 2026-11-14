# switchExpression: spike value graph

```mermaid
flowchart TD
  n0["App:25 CALL<br/>args.length"]
  n1["App:25 CALL<br/>int kind = args.length"]
  n2["App:25 IDENTIFIER<br/>args"]
  n3["App:25 IDENTIFIER<br/>kind"]
  n4["App:26 CALL<br/>int base = 7"]
  n5["App:26 IDENTIFIER<br/>base"]
  n6["App:26 LITERAL<br/>7"]
  n7["App:27 CALL<br/>int bonus = 11"]
  n8["App:27 IDENTIFIER<br/>bonus"]
  n9["App:27 LITERAL<br/>11"]
  n10["App:28 CALL<br/>int fallback = 13"]
  n11["App:28 IDENTIFIER<br/>fallback"]
  n12["App:28 LITERAL<br/>13"]
  n13["App:29 CALL<br/>int note = 0"]
  n14["App:29 IDENTIFIER<br/>note"]
  n15["App:29 LITERAL<br/>0"]
  n16["App:31 CALL<br/>int total = switch(kind) { case 1 -#gt; base; case 2 -#gt; { note = 41; int "]
  n17["App:31 CONTROL_STRUCTURE<br/>switch(kind)"]
  n18["App:31 IDENTIFIER<br/>kind"]
  n19["App:31 IDENTIFIER<br/>total"]
  n20["App:32 LITERAL<br/>1"]
  n21["App:33 LITERAL<br/>2"]
  n22["App:34 CALL<br/>note = 41"]
  n23["App:34 IDENTIFIER<br/>note"]
  n24["App:34 LITERAL<br/>41"]
  n25["App:35 CALL<br/>bonus * 2"]
  n26["App:35 CALL<br/>int doubled = bonus * 2"]
  n27["App:35 IDENTIFIER<br/>bonus"]
  n28["App:35 IDENTIFIER<br/>doubled"]
  n29["App:35 LITERAL<br/>2"]
  n30["App:38 CALL<br/>note - fallback"]
  n31["App:38 IDENTIFIER<br/>fallback"]
  n32["App:38 IDENTIFIER<br/>note"]
  n33["App:38 LITERAL<br/>3"]
  n34["App:39 CALL<br/>$obj0 = new IllegalStateException(#quot;unsupported#quot;)"]
  n35["App:39 CALL<br/>new IllegalStateException(#quot;unsupported#quot;)"]
  n36["App:39 CALL<br/>throw new IllegalStateException(#quot;unsupported#quot;);"]
  n37["App:39 IDENTIFIER<br/>$obj0"]
  n38["App:39 LITERAL<br/>#quot;unsupported#quot;"]
  n39["App:42 CALL<br/>int out = total + note"]
  n40["App:42 CALL<br/>total + note"]
  n41["App:42 IDENTIFIER<br/>note"]
  n42["App:42 IDENTIFIER<br/>out"]
  n43["App:42 IDENTIFIER<br/>total"]
  n0 -->|"local: args.length"| n1
  n1 -->|"local: ="| n3
  n10 -->|"local: ="| n11
  n12 -->|"local: 13"| n10
  n13 -->|"local: ="| n14
  n14 -->|"local: note"| n41
  n15 -->|"local: 0"| n13
  n16 -->|"local: ="| n19
  n17 -->|"local: switch(kind)"| n16
  n18 -->|"guard: guard kind"| n20
  n18 -->|"guard: guard kind"| n21
  n18 -->|"guard: guard kind"| n33
  n18 -->|"guard: guard kind"| n34
  n18 -->|"guard: guard kind"| n35
  n18 -->|"guard: guard kind"| n36
  n18 -->|"guard: guard kind"| n37
  n18 -->|"guard: guard kind"| n38
  n19 -->|"local: total"| n43
  n2 -->|"local: args"| n0
  n22 -->|"local: ="| n23
  n24 -->|"local: 41"| n22
  n25 -->|"local: bonus * 2"| n26
  n26 -->|"local: ="| n28
  n27 -->|"local: bonus"| n25
  n29 -->|"local: 2"| n25
  n31 -->|"local: fallback"| n30
  n32 -->|"local: note"| n30
  n32 -->|"local: note"| n41
  n34 -->|"local: ="| n37
  n35 -->|"local: new IllegalStateException(#quot;unsupported#quot;)"| n34
  n37 -->|"local: $obj0"| n35
  n37 -->|"local: $obj0"| n37
  n38 -->|"local: #quot;unsupported#quot;"| n35
  n39 -->|"local: ="| n42
  n4 -->|"local: ="| n5
  n40 -->|"local: total + note"| n39
  n41 -->|"local: note"| n40
  n43 -->|"local: total"| n40
  n6 -->|"local: 7"| n4
  n7 -->|"local: ="| n8
  n9 -->|"local: 11"| n7
```
