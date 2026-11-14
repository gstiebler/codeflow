# operators: spike value graph

```mermaid
flowchart TD
  n0["App:10 CALL<br/>int a = 10"]
  n1["App:10 IDENTIFIER<br/>a"]
  n2["App:10 LITERAL<br/>10"]
  n3["App:11 CALL<br/>int b = 3"]
  n4["App:11 IDENTIFIER<br/>b"]
  n5["App:11 LITERAL<br/>3"]
  n6["App:13 CALL<br/>a + b"]
  n7["App:13 CALL<br/>int sum = a + b"]
  n8["App:13 IDENTIFIER<br/>a"]
  n9["App:13 IDENTIFIER<br/>b"]
  n10["App:13 IDENTIFIER<br/>sum"]
  n11["App:14 CALL<br/>a - b"]
  n12["App:14 CALL<br/>int difference = a - b"]
  n13["App:14 IDENTIFIER<br/>a"]
  n14["App:14 IDENTIFIER<br/>b"]
  n15["App:14 IDENTIFIER<br/>difference"]
  n16["App:15 CALL<br/>a * b"]
  n17["App:15 CALL<br/>int product = a * b"]
  n18["App:15 IDENTIFIER<br/>a"]
  n19["App:15 IDENTIFIER<br/>b"]
  n20["App:15 IDENTIFIER<br/>product"]
  n21["App:16 CALL<br/>a / b"]
  n22["App:16 CALL<br/>int quotient = a / b"]
  n23["App:16 IDENTIFIER<br/>a"]
  n24["App:16 IDENTIFIER<br/>b"]
  n25["App:16 IDENTIFIER<br/>quotient"]
  n26["App:17 CALL<br/>a % b"]
  n27["App:17 CALL<br/>int remainder = a % b"]
  n28["App:17 IDENTIFIER<br/>a"]
  n29["App:17 IDENTIFIER<br/>b"]
  n30["App:17 IDENTIFIER<br/>remainder"]
  n31["App:19 CALL<br/>a == b"]
  n32["App:19 CALL<br/>boolean equal = a == b"]
  n33["App:19 IDENTIFIER<br/>a"]
  n34["App:19 IDENTIFIER<br/>b"]
  n35["App:19 IDENTIFIER<br/>equal"]
  n36["App:20 CALL<br/>a != b"]
  n37["App:20 CALL<br/>boolean notEqual = a != b"]
  n38["App:20 IDENTIFIER<br/>a"]
  n39["App:20 IDENTIFIER<br/>b"]
  n40["App:20 IDENTIFIER<br/>notEqual"]
  n41["App:21 CALL<br/>a #lt; b"]
  n42["App:21 CALL<br/>boolean less = a #lt; b"]
  n43["App:21 IDENTIFIER<br/>a"]
  n44["App:21 IDENTIFIER<br/>b"]
  n45["App:21 IDENTIFIER<br/>less"]
  n46["App:22 CALL<br/>a #gt; b"]
  n47["App:22 CALL<br/>boolean greater = a #gt; b"]
  n48["App:22 IDENTIFIER<br/>a"]
  n49["App:22 IDENTIFIER<br/>b"]
  n50["App:22 IDENTIFIER<br/>greater"]
  n51["App:23 CALL<br/>a #lt;= b"]
  n52["App:23 CALL<br/>boolean lessOrEqual = a #lt;= b"]
  n53["App:23 IDENTIFIER<br/>a"]
  n54["App:23 IDENTIFIER<br/>b"]
  n55["App:23 IDENTIFIER<br/>lessOrEqual"]
  n56["App:24 CALL<br/>a #gt;= b"]
  n57["App:24 CALL<br/>boolean greaterOrEqual = a #gt;= b"]
  n58["App:24 IDENTIFIER<br/>a"]
  n59["App:24 IDENTIFIER<br/>b"]
  n60["App:24 IDENTIFIER<br/>greaterOrEqual"]
  n61["App:26 CALL<br/>boolean both = equal && less"]
  n62["App:26 CALL<br/>equal && less"]
  n63["App:26 IDENTIFIER<br/>both"]
  n64["App:26 IDENTIFIER<br/>equal"]
  n65["App:26 IDENTIFIER<br/>less"]
  n66["App:27 CALL<br/>boolean either = equal || less"]
  n67["App:27 CALL<br/>equal || less"]
  n68["App:27 IDENTIFIER<br/>either"]
  n69["App:27 IDENTIFIER<br/>equal"]
  n70["App:27 IDENTIFIER<br/>less"]
  n0 -->|"local: ="| n1
  n1 -->|"local: a"| n8
  n11 -->|"local: a - b"| n12
  n12 -->|"local: ="| n15
  n13 -->|"local: a"| n11
  n13 -->|"local: a"| n18
  n14 -->|"local: b"| n11
  n14 -->|"local: b"| n19
  n16 -->|"local: a * b"| n17
  n17 -->|"local: ="| n20
  n18 -->|"local: a"| n16
  n18 -->|"local: a"| n23
  n19 -->|"local: b"| n16
  n19 -->|"local: b"| n24
  n2 -->|"local: 10"| n0
  n21 -->|"local: a / b"| n22
  n22 -->|"local: ="| n25
  n23 -->|"local: a"| n21
  n23 -->|"local: a"| n28
  n24 -->|"local: b"| n21
  n24 -->|"local: b"| n29
  n26 -->|"local: a % b"| n27
  n27 -->|"local: ="| n30
  n28 -->|"local: a"| n26
  n28 -->|"local: a"| n33
  n29 -->|"local: b"| n26
  n29 -->|"local: b"| n34
  n3 -->|"local: ="| n4
  n31 -->|"local: a == b"| n32
  n32 -->|"local: ="| n35
  n33 -->|"local: a"| n31
  n33 -->|"local: a"| n38
  n34 -->|"local: b"| n31
  n34 -->|"local: b"| n39
  n35 -->|"local: equal"| n64
  n36 -->|"local: a != b"| n37
  n37 -->|"local: ="| n40
  n38 -->|"local: a"| n36
  n38 -->|"local: a"| n43
  n39 -->|"local: b"| n36
  n39 -->|"local: b"| n44
  n4 -->|"local: b"| n9
  n41 -->|"local: a #lt; b"| n42
  n42 -->|"local: ="| n45
  n43 -->|"local: a"| n41
  n43 -->|"local: a"| n48
  n44 -->|"local: b"| n41
  n44 -->|"local: b"| n49
  n45 -->|"local: less"| n65
  n46 -->|"local: a #gt; b"| n47
  n47 -->|"local: ="| n50
  n48 -->|"local: a"| n46
  n48 -->|"local: a"| n53
  n49 -->|"local: b"| n46
  n49 -->|"local: b"| n54
  n5 -->|"local: 3"| n3
  n51 -->|"local: a #lt;= b"| n52
  n52 -->|"local: ="| n55
  n53 -->|"local: a"| n51
  n53 -->|"local: a"| n58
  n54 -->|"local: b"| n51
  n54 -->|"local: b"| n59
  n56 -->|"local: a #gt;= b"| n57
  n57 -->|"local: ="| n60
  n58 -->|"local: a"| n56
  n59 -->|"local: b"| n56
  n6 -->|"local: a + b"| n7
  n61 -->|"local: ="| n63
  n62 -->|"local: equal && less"| n61
  n64 -->|"guard: guard equal"| n65
  n64 -->|"local: equal"| n62
  n64 -->|"local: equal"| n69
  n65 -->|"local: less"| n62
  n65 -->|"local: less"| n70
  n66 -->|"local: ="| n68
  n67 -->|"local: equal || less"| n66
  n69 -->|"guard: guard equal"| n70
  n69 -->|"local: equal"| n67
  n7 -->|"local: ="| n10
  n70 -->|"local: less"| n67
  n8 -->|"local: a"| n13
  n8 -->|"local: a"| n6
  n9 -->|"local: b"| n14
  n9 -->|"local: b"| n6
```
