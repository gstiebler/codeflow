# instanceOf: spike value graph

```mermaid
flowchart TD
  n0["App:4 METHOD_PARAMETER_IN<br/>String[] args"]
  n1["App:5 CALL<br/>Object value = args"]
  n2["App:5 IDENTIFIER<br/>args"]
  n3["App:5 IDENTIFIER<br/>value"]
  n4["App:6 CALL<br/>boolean isText = value instanceof String"]
  n5["App:6 CALL<br/>value instanceof String"]
  n6["App:6 IDENTIFIER<br/>isText"]
  n7["App:6 IDENTIFIER<br/>value"]
  n8["App:6 TYPE_REF<br/>String"]
  n9["App:7 CALL<br/>(String) value"]
  n10["App:7 CALL<br/>(value instanceof String) && { text = (String) value; true; }"]
  n11["App:7 CALL<br/>text = (String) value"]
  n12["App:7 CALL<br/>value instanceof String"]
  n13["App:7 IDENTIFIER<br/>text"]
  n14["App:7 IDENTIFIER<br/>value"]
  n15["App:7 LITERAL<br/>true"]
  n16["App:7 TYPE_REF<br/>String"]
  n17["App:8 CALL<br/>int size = text.length()"]
  n18["App:8 CALL<br/>length()"]
  n19["App:8 IDENTIFIER<br/>size"]
  n20["App:8 IDENTIFIER<br/>text"]
  n0 -->|"local: args"| n2
  n1 -->|"local: ="| n3
  n10 -->|"guard: guard (value instanceof String) && { text = (String) value; true; }"| n17
  n10 -->|"guard: guard (value instanceof String) && { text = (String) value; true; }"| n18
  n10 -->|"guard: guard (value instanceof String) && { text = (String) value; true; }"| n19
  n10 -->|"guard: guard (value instanceof String) && { text = (String) value; true; }"| n20
  n11 -->|"local: ="| n13
  n12 -->|"guard: guard value instanceof String"| n11
  n12 -->|"guard: guard value instanceof String"| n13
  n12 -->|"guard: guard value instanceof String"| n14
  n12 -->|"guard: guard value instanceof String"| n15
  n12 -->|"guard: guard value instanceof String"| n16
  n12 -->|"guard: guard value instanceof String"| n9
  n12 -->|"local: value instanceof String"| n10
  n13 -->|"local: text"| n20
  n14 -->|"local: value"| n12
  n14 -->|"local: value"| n14
  n14 -->|"local: value"| n9
  n16 -->|"local: String"| n12
  n16 -->|"local: String"| n9
  n17 -->|"local: ="| n19
  n18 -->|"local: length()"| n17
  n2 -->|"local: args"| n1
  n20 -->|"local: text"| n18
  n3 -->|"local: value"| n7
  n4 -->|"local: ="| n6
  n5 -->|"local: value instanceof String"| n4
  n7 -->|"local: value"| n14
  n7 -->|"local: value"| n5
  n8 -->|"local: String"| n5
  n9 -->|"local: (String) value"| n11
```
