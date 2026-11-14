# classLiteral: spike value graph

```mermaid
flowchart TD
  n0["App:17 CALL<br/>Class primitive = boolean.class"]
  n1["App:17 CALL<br/>boolean.class"]
  n2["App:17 FIELD_IDENTIFIER<br/>class"]
  n3["App:17 IDENTIFIER<br/>boolean"]
  n4["App:17 IDENTIFIER<br/>primitive"]
  n5["App:18 CALL<br/>Class reference = String.class"]
  n6["App:18 CALL<br/>String.class"]
  n7["App:18 FIELD_IDENTIFIER<br/>class"]
  n8["App:18 IDENTIFIER<br/>String"]
  n9["App:18 IDENTIFIER<br/>reference"]
  n10["App:19 CALL<br/>register(primitive, reference)"]
  n11["App:19 IDENTIFIER<br/>primitive"]
  n12["App:19 IDENTIFIER<br/>reference"]
  n13["App:19 IDENTIFIER<br/>this"]
  n14["App:22 METHOD_PARAMETER_IN<br/>Class#lt;?#gt; first"]
  n15["App:22 METHOD_PARAMETER_IN<br/>Class#lt;?#gt; second"]
  n16["App:23 CALL<br/>String pair = first.getName() + second.getName()"]
  n17["App:23 CALL<br/>first.getName() + second.getName()"]
  n18["App:23 CALL<br/>getName()"]
  n19["App:23 IDENTIFIER<br/>first"]
  n20["App:23 IDENTIFIER<br/>pair"]
  n21["App:23 IDENTIFIER<br/>second"]
  n0 -->|"local: ="| n4
  n1 -->|"local: field boolean.class"| n0
  n11 -->|"arg: arg 1 first"| n14
  n11 -->|"local: primitive"| n10
  n12 -->|"arg: arg 2 second"| n15
  n12 -->|"local: reference"| n10
  n13 -->|"local: this"| n10
  n14 -->|"local: first"| n19
  n15 -->|"local: second"| n21
  n16 -->|"local: ="| n20
  n17 -->|"local: first.getName() + second.getName()"| n16
  n18 -->|"local: getName()"| n17
  n19 -->|"local: first"| n18
  n2 -->|"local: class"| n1
  n21 -->|"local: second"| n18
  n3 -->|"local: boolean"| n1
  n4 -->|"local: primitive"| n11
  n5 -->|"local: ="| n9
  n6 -->|"local: field ANY.class"| n5
  n7 -->|"local: class"| n6
  n8 -->|"local: String"| n6
  n9 -->|"local: reference"| n12
```
